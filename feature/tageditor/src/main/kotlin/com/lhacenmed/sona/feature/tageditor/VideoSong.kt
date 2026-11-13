package com.lhacenmed.sona.feature.tageditor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.lhacenmed.sona.feature.tageditor.lookup.CatalogueMatch
import com.lhacenmed.sona.feature.tageditor.lookup.TrackMatcher
import com.lhacenmed.sona.feature.tageditor.lookup.TrackQueries
import com.lhacenmed.sona.feature.tageditor.lookup.TrackQuery
import com.lhacenmed.sona.feature.tageditor.lyrics.FoundLyrics
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.feature.tageditor.tags.TrackTags
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How the lyrics a video's sound is saved with were chosen. */
sealed interface LyricsPick {
    /** The best that can be trusted unasked - see [FoundLyrics.isTrustedFor] - as the sources answer. */
    data object Best : LyricsPick

    data class Found(val lyrics: FoundLyrics) : LyricsPick

    /** Written or pasted by the user. */
    data class Written(val text: String) : LyricsPick

    data object None : LyricsPick
}

/**
 * The song a video's sound is saved as - everything the tag editor finds for a track, found for a video before it is
 * downloaded: the song its [title] by [channel] surely is, with every tag its catalogue knows, its cover and the lyrics
 * timed to a recording of the video's length - each picked unasked, each changed with a tap. What it comes to is kept with
 * the download as its [plan], and written into the file once it is saved - see [SongTagging.tag].
 */
@Stable
class VideoSong internal constructor(
    private val title: String,
    private val channel: String?,
    durationMs: Long,
    /** The video's own picture - the cover its file is saved with unless another is picked. */
    val videoCoverUrl: String?,
    private val scope: CoroutineScope,
) : SongDraft(durationMs, scope) {

    private val readings: List<TrackQuery> = SongTagging.readingsOf(title, channel)

    /** What a search for the song starts from: the video's likeliest reading, cleaned of what no catalogue names. */
    val searchText: String = readings.firstOrNull()?.text ?: title

    /** The song picked - surely found, or by the user - with every tag its catalogue knows; null while there is none. */
    var song by mutableStateOf<CatalogueMatch?>(null)
        private set

    /** Whether the sound is saved as this song: surely found, or picked or confirmed by the user. */
    var isChosen by mutableStateOf(false)
        private set

    /** Whether the song is being found - unasked, or the one picked being completed. */
    var isFinding by mutableStateOf(true)
        private set

    var lyricsPick by mutableStateOf<LyricsPick>(LyricsPick.Best)
        private set

    /** The lyrics found that the sound is saved with, as they stand - null for none, or none yet. */
    val lyrics: FoundLyrics?
        get() = when (val pick = lyricsPick) {
            LyricsPick.Best -> trustedLyrics
            is LyricsPick.Found -> pick.lyrics
            else -> null
        }

    /**
     * The covers to pick from: the song's own, then the same song's from every other catalogue - the first of each that is
     * surely it - each its catalogue's.
     */
    val catalogueCovers: List<CatalogueMatch> by derivedStateOf {
        val picked = song ?: return@derivedStateOf emptyList()
        val query = TrackQuery(picked.artist, picked.title, TrackMatcher.versionOf(picked.title))
        val others = (matchLookup as? MatchLookup.Found)?.matches.orEmpty()
            .filter { it.catalogue != picked.catalogue && it.coverUrl.isNotBlank() && TrackMatcher.score(query, it) >= TrackMatcher.Confident }
            .distinctBy { it.catalogue }
        listOfNotNull(picked.takeIf { it.coverUrl.isNotBlank() }) + others
    }

    private var findJob: Job? = null
    private var searchJob: Job? = null

    /** Finds the song, unasked: the catalogues' best taken where it surely is the video's; its lyrics looked for either way. */
    internal fun find() {
        findJob = scope.launch {
            findMatches(readings)
            val best = matches.firstOrNull()
            if (best != null && readings.any { TrackMatcher.score(it, best) >= TrackMatcher.Confident }) {
                take(best)
            } else {
                isFinding = false
                readings.firstOrNull()?.let { findLyrics(lyricsQuery(it.title, it.artist, album = null)) }
            }
        }
    }

    /**
     * The song as it stands once it is no longer being looked for - the best found unasked, or the one picked - null where none
     * surely is: what is looked for in the library before it downloads. See `LibraryCopies`.
     */
    suspend fun awaitFound(): SongSummary? {
        snapshotFlow { isFinding }.first { !it }
        return song?.takeIf { isChosen }?.let { SongSummary(it.title, it.artist, it.tags[TagField.ALBUM], it.coverUrl.ifBlank { null }) }
    }

    /** Searches the catalogues for the song [text] names - "Artist - Title", or a title alone - the song picked left as it is. */
    fun searchFor(text: String) {
        if (text.isBlank()) return
        searchJob?.cancel()
        searchJob = scope.launch { findMatches(TrackQueries.of(title = text, artist = "", fileName = "")) }
    }

    /** [match] as the song: every tag its catalogue knows, its cover, and the lyrics looked for again under its names. */
    fun pick(match: CatalogueMatch) {
        findJob?.cancel()
        findJob = scope.launch { take(match) }
    }

    private suspend fun take(match: CatalogueMatch) {
        isFinding = true
        val complete = complete(match)
        song = complete
        isChosen = true
        tags = complete.tags
        cover = complete.coverUrl.takeIf { it.isNotBlank() }?.let { CoverChoice.Web(it) } ?: CoverChoice.Own
        lyricsPick = LyricsPick.Best
        isFinding = false
        findLyrics(lyricsQuery(complete.title, complete.artist, complete.tags[TagField.ALBUM].ifBlank { null }))
    }

    /** A picture from the gallery as the cover - kept by the app, as the download may start after the gallery's leave to read it ends. */
    fun pickPicture(context: Context, uri: Uri) {
        scope.launch {
            val kept = runCatching {
                withContext(Dispatchers.IO) {
                    val file = File(File(context.cacheDir, PicturesFolder).apply { mkdirs() }, "${System.nanoTime()}.jpg")
                    file.writeBytes(scaledCoverOf(context, uri))
                    Uri.fromFile(file).toString()
                }
            }.getOrNull() ?: return@launch
            pickDeviceCover(kept)
        }
    }

    fun chooseLyrics(found: FoundLyrics) {
        lyricsPick = LyricsPick.Found(found)
    }

    /** Lyrics the user wrote or pasted - none, where [text] is blank. */
    fun writeLyrics(text: String) {
        lyricsPick = if (text.isBlank()) LyricsPick.None else LyricsPick.Written(text.trim())
    }

    fun skipLyrics() {
        lyricsPick = LyricsPick.None
    }

    /** Whether [field] is no longer what the song has. */
    fun isEdited(field: TagField): Boolean = song?.let { tags[field] != it.tags[field] } == true

    /** Every tag back to what the song has. */
    fun resetTags() {
        tags = song?.tags ?: TrackTags()
    }

    /** The song, as it stands, confirmed as the one to save the sound as - where it has a title to be saved under. */
    fun confirm() {
        isChosen = tags[TagField.TITLE].isNotBlank()
    }

    /** What the download keeps to write into its file - null where the sound is not saved as a song chosen. */
    val plan: String?
        get() {
            if (!isChosen) return null
            val lyrics = when (val pick = lyricsPick) {
                // Still being found: the download finds them itself, as it does a song it was not given.
                LyricsPick.Best -> if (isSearchingLyrics) null else trustedLyrics?.lyrics?.text.orEmpty()
                is LyricsPick.Found -> pick.lyrics.lyrics.text
                is LyricsPick.Written -> pick.text
                LyricsPick.None -> ""
            }
            return SongPlan(tags.with(TagField.LYRICS, ""), cover, lyrics).encoded
        }

    /** Stops everything still being found - the song let go. */
    fun close() = scope.cancel()

    internal companion object {
        /** Where the pictures picked from the gallery are kept until their downloads are tagged - see [SongTagging.tag]. */
        const val PicturesFolder = "song-covers"
    }
}
