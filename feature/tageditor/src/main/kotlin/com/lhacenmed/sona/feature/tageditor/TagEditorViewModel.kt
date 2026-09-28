package com.lhacenmed.sona.feature.tageditor

import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.os.Build
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.UnknownNames
import com.lhacenmed.sona.feature.tageditor.lookup.CatalogueMatch
import com.lhacenmed.sona.feature.tageditor.lookup.TagLookup
import com.lhacenmed.sona.feature.tageditor.lookup.TrackMatcher
import com.lhacenmed.sona.feature.tageditor.lookup.TrackQueries
import com.lhacenmed.sona.feature.tageditor.lookup.TrackQuery
import com.lhacenmed.sona.feature.tageditor.lyrics.FoundLyrics
import com.lhacenmed.sona.feature.tageditor.lyrics.LyricsQuery
import com.lhacenmed.sona.feature.tageditor.lyrics.OnlineLyrics
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.feature.tageditor.tags.TrackTags
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

/** How saving ended: done, waiting on the user to let Sona change the file, or failed. */
sealed interface SaveOutcome {
    data object Saved : SaveOutcome
    data class NeedsConsent(val request: IntentSender) : SaveOutcome
    data class Failed(val message: String) : SaveOutcome
}

/**
 * The track [trackId]'s tags as a draft that reaches its file only on [save] - AutomaTag's editor.
 *
 * The moment it opens, the track is looked up in the catalogues and its lyrics on the web, both at once, under
 * the likeliest reading of its names - which is then laid out as [queryArtist] and [queryTitle], to be changed
 * and searched again. What is found can be narrowed to one catalogue or one lyrics source without asking
 * anything again. Every match applies in one press, every set of lyrics in another, and any field can be
 * changed by hand, before or after.
 *
 * The draft is snapshot state, so a field is updated in the frame it is typed in.
 */
@HiltViewModel(assistedFactory = TagEditorViewModel.Factory::class)
class TagEditorViewModel @AssistedInject constructor(
    @Assisted private val trackId: Long,
    libraryRepository: LibraryRepository,
    private val tagEditorRepository: TagEditorRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(trackId: Long): TagEditorViewModel
    }

    val catalogueNames: List<String> = TagLookup.catalogueNames
    val lyricsSourceNames: List<String> = OnlineLyrics.sourceNames

    var track by mutableStateOf<Track?>(null)
        private set

    /** The tags the file holds, or null until they have been read. */
    var original by mutableStateOf<TrackTags?>(null)
        private set

    var bitrateKbps by mutableStateOf<Long?>(null)
        private set

    var draft by mutableStateOf(TrackTags())
        private set

    var cover by mutableStateOf<CoverChoice>(CoverChoice.Own)
        private set

    /** The picture picked from the gallery, kept among the covers once picked, or null before one is. */
    var deviceCoverUri by mutableStateOf<String?>(null)
        private set

    var queryArtist by mutableStateOf("")
    var queryTitle by mutableStateOf("")

    var matchLookup by mutableStateOf<MatchLookup>(MatchLookup.Searching)
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

    var isSearchingLyrics by mutableStateOf(true)
        private set

    /** The lyrics source the results are narrowed to, or null for all of them. */
    var lyricsFilter by mutableStateOf<String?>(null)
        private set

    val visibleLyrics: List<FoundLyrics> by derivedStateOf {
        lyricsResults.filter { lyricsFilter == null || it.source == lyricsFilter }
    }

    var isSaving by mutableStateOf(false)
        private set

    val hasChanges: Boolean get() = original.let { it != null && (it != draft || cover != CoverChoice.Own) }

    private var matchJob: Job? = null
    private var lyricsJob: Job? = null

    init {
        viewModelScope.launch {
            val found = libraryRepository.tracksById.map { it[trackId] }.filterNotNull().first()
            track = found
            // Read before anything can be applied over them - a matter of milliseconds, as the file is local.
            val info = tagEditorRepository.read(found)
            original = info.tags
            draft = info.tags
            bitrateKbps = info.bitrateKbps
            val readings = TrackQueries.of(
                title = found.title,
                artist = found.artist.takeUnless { it == UnknownNames.ARTIST }.orEmpty(),
                fileName = File(found.path).nameWithoutExtension,
            )
            readings.firstOrNull()?.let {
                queryArtist = it.artist
                queryTitle = it.title
            }
            search(readings, album = found.album.takeUnless { it == UnknownNames.ALBUM })
        }
    }

    /** Looks up [queryArtist] and [queryTitle] as they now stand - the search, pressed. */
    fun search() {
        if (queryTitle.isBlank()) return
        // Without the album: one typed in is a new search, which the file's album may no longer fit.
        search(listOf(TrackQuery(queryArtist.trim(), queryTitle.trim(), TrackMatcher.versionOf(queryTitle))), album = null)
    }

    fun filterCatalogue(name: String?) {
        catalogueFilter = name
        completeBest()
    }

    fun filterLyrics(source: String?) {
        lyricsFilter = source
    }

    fun setField(field: TagField, value: String) {
        draft = draft.with(field, if (field.isNumeric) value.filter(Char::isDigit) else value)
    }

    fun chooseCover(choice: CoverChoice) {
        cover = choice
    }

    fun pickDeviceCover(uri: String) {
        deviceCoverUri = uri
        cover = CoverChoice.Device(uri)
    }

    /** [match] with every tag its catalogue knows, where that is already known. */
    fun completed(match: CatalogueMatch): CatalogueMatch = completedMatches[match] ?: match

    /** Takes every tag [match] has, and its cover, into the draft - completing it first where it is not yet. */
    fun apply(match: CatalogueMatch) {
        completedMatches[match]?.let {
            applyComplete(it, listed = match)
            return
        }
        applyingMatch = match
        viewModelScope.launch {
            val complete = complete(match)
            if (applyingMatch == match) {
                applyingMatch = null
                applyComplete(complete, listed = match)
            }
        }
    }

    fun applyLyrics(found: FoundLyrics) {
        setField(TagField.LYRICS, found.lyrics.text)
    }

    /** Writes the draft into the file; [onOutcome] hears how it went. */
    fun save(onOutcome: (SaveOutcome) -> Unit) {
        val track = track ?: return
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            val outcome = try {
                tagEditorRepository.save(track, draft, cover)
                SaveOutcome.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    SaveOutcome.NeedsConsent(e.userAction.actionIntent.intentSender)
                } else {
                    SaveOutcome.Failed(e.message ?: "The tags could not be saved")
                }
            }
            isSaving = false
            onOutcome(outcome)
        }
    }

    private fun applyComplete(match: CatalogueMatch, listed: CatalogueMatch) {
        draft = draft.overlaidWith(match.tags)
        match.coverUrl.takeIf { it.isNotBlank() }?.let { cover = CoverChoice.Web(it) }
        appliedMatch = listed
    }

    private suspend fun complete(match: CatalogueMatch): CatalogueMatch =
        completedMatches[match] ?: TagLookup.complete(match).also { completedMatches[match] = it }

    /** Completes the match now offered as the best, so its card shows every tag it would fill in. */
    private fun completeBest() {
        val best = matches.firstOrNull() ?: return
        if (best !in completedMatches) viewModelScope.launch { complete(best) }
    }

    /** Looks [readings] up in the catalogues, and the first of them - on [album] - for lyrics on the web, both at once. */
    private fun search(readings: List<TrackQuery>, album: String?) {
        val track = track ?: return
        val reading = readings.firstOrNull() ?: return

        matchJob?.cancel()
        matchLookup = MatchLookup.Searching
        appliedMatch = null
        matchJob = viewModelScope.launch {
            val found = TagLookup.search(readings)
            matchLookup = when {
                found == null -> MatchLookup.Unreachable
                found.isEmpty() -> MatchLookup.NotFound
                else -> MatchLookup.Found(found)
            }
            completeBest()
        }

        lyricsJob?.cancel()
        lyricsResults = emptyList()
        isSearchingLyrics = true
        val query = LyricsQuery(
            title = reading.title,
            artist = reading.artist,
            album = album,
            durationSeconds = (track.durationMs / 1000).toInt().takeIf { it > 0 } ?: -1,
        )
        val order = OnlineLyrics.order(track.durationMs)
        lyricsJob = viewModelScope.launch {
            OnlineLyrics.search(query).collect { found ->
                lyricsResults = (lyricsResults + found).sortedWith(order)
            }
            isSearchingLyrics = false
        }
    }
}
