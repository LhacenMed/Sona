package com.lhacenmed.sona.feature.tageditor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.tageditor.lookup.CatalogueMatch
import com.lhacenmed.sona.feature.tageditor.lookup.TagLookup
import com.lhacenmed.sona.feature.tageditor.lookup.TrackMatcher
import com.lhacenmed.sona.feature.tageditor.lookup.TrackQuery
import com.lhacenmed.sona.feature.tageditor.lyrics.FoundLyrics
import com.lhacenmed.sona.feature.tageditor.lyrics.LyricsQuery
import com.lhacenmed.sona.feature.tageditor.lyrics.OnlineLyrics
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.feature.tageditor.tags.TagFileInfo
import com.lhacenmed.sona.feature.tageditor.tags.TrackTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where the lookup of a track's tags in the catalogues stands. */
sealed interface MatchLookup {
    data object Searching : MatchLookup

    /** Every song the catalogues answered with, best first. */
    data class Found(val matches: List<CatalogueMatch>) : MatchLookup

    data object NotFound : MatchLookup

    /** No catalogue could be reached - the one outcome worth trying again. */
    data object Unreachable : MatchLookup
}

/**
 * [track]'s tags as a draft over the [original] its [file] holds - made from the track alone, so whatever shows it
 * has it from its first frame, and taking the file's tags as they are read - and everything found to fill it with - every
 * catalogue's matches, every source's lyrics, every cover - the one state the tag editor edits a track in, and an
 * auto-tag run each of its tracks. Nothing here reaches the file: whoever holds the draft saves it.
 *
 * What is found can be narrowed to one catalogue or one lyrics source without asking anything again. Every match
 * applies in one press, every set of lyrics in another, and any field can be changed by hand, before or after.
 * Lookups run in [scope], so they outlive whatever screen shows the draft for as long as its holder does.
 *
 * Snapshot state, so a field is updated in the frame it is typed in.
 */
@Stable
class TrackTagDraft(
    val track: Track,
    private val scope: CoroutineScope,
) {
    /** What the user has made of the draft - its tags, its cover and the match they came from - to come back to. */
    data class Edits(val tags: TrackTags, val cover: CoverChoice, val appliedMatch: CatalogueMatch?)

    /** What the file held as it was read, or null until it has been - see [takeFile]. */
    var file by mutableStateOf<TagFileInfo?>(null)
        private set

    val original: TrackTags get() = file?.tags ?: TrackTags()

    val catalogueNames: List<String> = TagLookup.catalogueNames
    val lyricsSourceNames: List<String> = OnlineLyrics.sourceNames

    var tags by mutableStateOf(TrackTags())
        private set

    /** Takes what the file holds, read, as what the draft starts from - before anything can be applied over it. */
    fun takeFile(info: TagFileInfo) {
        file = info
        tags = info.tags
    }

    var cover by mutableStateOf<CoverChoice>(CoverChoice.Own)
        private set

    /** The picture picked from the gallery, kept among the covers once picked, or null before one is. */
    var deviceCoverUri by mutableStateOf<String?>(null)
        private set

    var queryArtist by mutableStateOf("")
    var queryTitle by mutableStateOf("")

    var matchLookup by mutableStateOf<MatchLookup>(MatchLookup.NotFound)
        private set

    /** The catalogue the matches are narrowed to, or null for all of them. */
    var catalogueFilter by mutableStateOf<String?>(null)
        private set

    /** The matches as they are narrowed - the first is the one offered as the best. */
    val matches: List<CatalogueMatch> by derivedStateOf {
        (matchLookup as? MatchLookup.Found)?.matches.orEmpty().filter { catalogueFilter == null || it.catalogue == catalogueFilter }
    }

    /** Matches with every tag their catalogue knows - asked for the best one as soon as it is shown. */
    private val completedMatches = mutableStateMapOf<CatalogueMatch, CatalogueMatch>()

    /** The match whose tags the draft took last, as it is listed, or null for none. */
    var appliedMatch by mutableStateOf<CatalogueMatch?>(null)
        private set

    /** The match being completed before it is applied. */
    var applyingMatch by mutableStateOf<CatalogueMatch?>(null)
        private set

    /** Every set of lyrics found so far, best first. */
    var lyricsResults by mutableStateOf<List<FoundLyrics>>(emptyList())
        private set

    var isSearchingLyrics by mutableStateOf(false)
        private set

    /** The lyrics source the results are narrowed to, or null for all of them. */
    var lyricsFilter by mutableStateOf<String?>(null)
        private set

    val visibleLyrics: List<FoundLyrics> by derivedStateOf {
        lyricsResults.filter { lyricsFilter == null || it.source == lyricsFilter }
    }

    /** The best lyrics found that can be given the track unasked - see [FoundLyrics.isTrustedFor]. */
    val trustedLyrics: FoundLyrics? get() = lyricsResults.firstOrNull { it.isTrustedFor(track.durationMs) }

    val hasChanges: Boolean get() = tags != original || cover != CoverChoice.Own

    val edits: Edits get() = Edits(tags, cover, appliedMatch)

    /** Goes back to [edits], as the draft stood when they were taken. */
    fun restore(edits: Edits) {
        tags = edits.tags
        cover = edits.cover
        appliedMatch = edits.appliedMatch
    }

    private var lookupJob: Job? = null

    /** Looks up [queryArtist] and [queryTitle] as they now stand - the search, pressed. */
    fun search() {
        if (queryTitle.isBlank()) return
        // Without the album: one typed in is a new search, which the file's album may no longer fit.
        lookUp(listOf(TrackQuery(queryArtist.trim(), queryTitle.trim(), TrackMatcher.versionOf(queryTitle))), album = null)
    }

    /** Looks [readings] up in the catalogues, and the first of them - on [album] - for lyrics on the web, both at once. */
    internal fun lookUp(readings: List<TrackQuery>, album: String?) {
        val reading = readings.firstOrNull() ?: return
        lookupJob?.cancel()
        // Marked at once, so nothing reads the last search's results as this one's.
        matchLookup = MatchLookup.Searching
        isSearchingLyrics = true
        lookupJob = scope.launch {
            launch { findMatches(readings) }
            launch { findLyrics(lyricsQuery(reading.title, reading.artist, album)) }
        }
    }

    /** Looks [readings] up in the catalogues, laying the first out as the search, and completes the best match found. */
    internal suspend fun findMatches(readings: List<TrackQuery>) {
        readings.firstOrNull()?.let {
            queryArtist = it.artist
            queryTitle = it.title
        }
        matchLookup = MatchLookup.Searching
        appliedMatch = null
        val found = TagLookup.search(readings)
        matchLookup = when {
            found == null -> MatchLookup.Unreachable
            found.isEmpty() -> MatchLookup.NotFound
            else -> MatchLookup.Found(found)
        }
        matches.firstOrNull()?.let { complete(it) }
    }

    /**
     * Looks for [query]'s lyrics on the web, every set laid out as it arrives; once every source has answered, only
     * the [kept] best are kept, with the [trustedLyrics] among them wherever they rank.
     */
    internal suspend fun findLyrics(query: LyricsQuery, kept: Int = Int.MAX_VALUE) {
        lyricsResults = emptyList()
        isSearchingLyrics = true
        val order = OnlineLyrics.order(track.durationMs)
        OnlineLyrics.search(query).collect { found -> lyricsResults = (lyricsResults + found).sortedWith(order) }
        if (lyricsResults.size > kept) lyricsResults = (lyricsResults.take(kept) + listOfNotNull(trustedLyrics)).distinct()
        isSearchingLyrics = false
    }

    /** What lyrics are looked for under: [title] by [artist], on [album] where it is known, at the track's length. */
    internal fun lyricsQuery(title: String, artist: String, album: String?): LyricsQuery = LyricsQuery(
        title = title,
        artist = artist,
        album = album,
        durationSeconds = (track.durationMs / 1000).toInt().takeIf { it > 0 } ?: -1,
    )

    fun filterCatalogue(name: String?) {
        catalogueFilter = name
        // Completes the match now offered as the best, so its card shows every tag it would fill in.
        matches.firstOrNull()?.takeIf { it !in completedMatches }?.let { scope.launch { complete(it) } }
    }

    fun filterLyrics(source: String?) {
        lyricsFilter = source
    }

    fun setField(field: TagField, value: String) {
        tags = tags.with(field, if (field.isNumeric) value.filter(Char::isDigit) else value)
    }

    fun chooseCover(choice: CoverChoice) {
        cover = choice
    }

    fun pickDeviceCover(uri: String) {
        deviceCoverUri = uri
        chooseCover(CoverChoice.Device(uri))
    }

    /** [match] with every tag its catalogue knows, where that is already known. */
    fun completed(match: CatalogueMatch): CatalogueMatch = completedMatches[match] ?: match

    /** Takes every tag [match] has, and its cover, into the draft - completing it first where it is not yet. */
    fun apply(match: CatalogueMatch) {
        completedMatches[match]?.let {
            takeMatch(tags.overlaidWith(it.tags), it.coverUrl, listed = match)
            return
        }
        applyingMatch = match
        scope.launch {
            val complete = complete(match)
            if (applyingMatch == match) {
                applyingMatch = null
                takeMatch(tags.overlaidWith(complete.tags), complete.coverUrl, listed = match)
            }
        }
    }

    /** Takes [tags] - what [listed] fills in, however much of it was taken - into the draft, with [coverUrl] as the cover where there is one. */
    internal fun takeMatch(tags: TrackTags, coverUrl: String, listed: CatalogueMatch) {
        this.tags = tags
        coverUrl.takeIf { it.isNotBlank() }?.let { chooseCover(CoverChoice.Web(it)) }
        appliedMatch = listed
    }

    fun applyLyrics(found: FoundLyrics) {
        setField(TagField.LYRICS, found.lyrics.text)
    }

    /** [match] with every tag its catalogue knows, asked for once. */
    internal suspend fun complete(match: CatalogueMatch): CatalogueMatch =
        completedMatches[match] ?: TagLookup.complete(match).also { completedMatches[match] = it }
}
