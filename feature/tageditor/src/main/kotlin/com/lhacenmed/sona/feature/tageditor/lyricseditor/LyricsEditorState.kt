package com.lhacenmed.sona.feature.tageditor.lyricseditor

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.tageditor.lyrics.FoundLyrics
import com.lhacenmed.sona.feature.tageditor.lyrics.OnlineLyrics
import com.lhacenmed.sona.feature.tageditor.lyrics.lyricsQueryOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [track]'s lyrics being timed by hand, as lines - ArchiveTune's synced lyrics editor - starting from
 * [initialLyrics]: synced or plain, from the file or the tag editor's draft. Held by the view model of the screen
 * it is shown on, which decides what becomes of [lyrics] once it is done - see [LyricsEditor].
 *
 * The lines are snapshot state, so a change shows in the frame it is made in. They keep the order they are sung in:
 * by time as they come in, and a line keeps its place while its time changes - stamping each in turn, from the top,
 * never moves one away from under the finger. They are put in time order as the lyrics are taken from here.
 *
 * Changing a line's time plays the track from it, so what was set is heard at once - the track started where it
 * is not the one playing.
 *
 * Every change to the lines is one step that [undo] takes back and [redo] makes again - lyrics brought in included,
 * with how their lines came in, so undoing them brings back the marks of the ones they replaced.
 */
@Stable
class LyricsEditorState internal constructor(
    val track: Track,
    initialLyrics: String,
    private val playbackController: PlaybackController,
    private val scope: CoroutineScope,
) {
    private var lastLineId = 0L

    var lines by mutableStateOf(lyricsLinesOf(initialLyrics, ::nextLineId).orEmpty())
        private set

    private val initialText = lyricsTextOf(lines)

    /** Each line as it came in, by its id - what [isEdited] compares with. */
    private var arrivedLines: Map<Long, LyricsLine> = lines.associateBy { it.id }

    /** The lines as they were before each change, the latest last - what [undo] goes back to. */
    private var undoRevisions by mutableStateOf(emptyList<Revision>())

    /** The lines as they were before each undo, the latest last - what [redo] goes forward to. */
    private var redoRevisions by mutableStateOf(emptyList<Revision>())

    val canUndo: Boolean get() = undoRevisions.isNotEmpty()

    val canRedo: Boolean get() = redoRevisions.isNotEmpty()

    /** The lines as lyrics are kept - see [lyricsTextOf]. */
    val lyrics: String by derivedStateOf { lyricsTextOf(lines) }

    val hasChanges: Boolean by derivedStateOf { lyrics != initialText }

    /** Every set of lyrics found on the web so far, best first. */
    var foundLyrics by mutableStateOf<List<FoundLyrics>>(emptyList())
        private set

    var isSearching by mutableStateOf(false)
        private set

    /** What is playing, and where - what stamping and the line marked as sung follow. */
    val playback: StateFlow<PlaybackUiState> = playbackController.playbackState

    private var searchJob: Job? = null

    /** Where the player is in the track now - read from it, finer than [playback]'s ticks. */
    fun currentPositionMs(): Long = playbackController.currentPositionMs()

    /** Whether [line] has been changed by hand since it came in - added, retimed or rewritten. */
    fun isEdited(line: LyricsLine): Boolean = arrivedLines[line.id] != line

    /** Sets the line's time to [timeMs] - typed in, or nudged - and plays from there. */
    fun setTime(lineId: Long, timeMs: Long) {
        val time = timeMs.coerceAtLeast(0L)
        update(lineId) { it.copy(timeMs = time) }
        playAt(time)
    }

    /**
     * Times the line at where the player is now - the moment its stamp button is pressed, while the track plays - and
     * plays on from there: going there again would only stutter.
     */
    fun stamp(lineId: Long) {
        update(lineId) { it.copy(timeMs = currentPositionMs()) }
        if (!playback.value.isPlaying) playbackController.togglePlayPause()
    }

    fun resetTime(lineId: Long) = update(lineId) { it.copy(timeMs = 0L) }

    fun setText(lineId: Long, text: String) = update(lineId) { it.copy(text = text.trim()) }

    fun remove(lineId: Long) = edit(lines.filterNot { it.id == lineId })

    /** Takes the lines [lineIds] names out. */
    fun remove(lineIds: Set<Long>) = edit(lines.filterNot { it.id in lineIds })

    /** Moves the times of the lines [lineIds] names by [deltaMs] - none before the start - and plays from the earliest of them. */
    fun offsetTimes(lineIds: Set<Long>, deltaMs: Long) {
        edit(lines.map { if (it.id in lineIds) it.copy(timeMs = (it.timeMs + deltaMs).coerceAtLeast(0L)) else it })
        lines.filter { it.id in lineIds }.minOfOrNull { it.timeMs }?.let(::playAt)
    }

    /** Adds an empty, untimed line right [below] - or above - the line [lineId], to be written and timed. */
    fun addLine(lineId: Long, below: Boolean) {
        val index = lines.indexOfFirst { it.id == lineId }.takeIf { it >= 0 } ?: return
        edit(lines.toMutableList().apply { add(if (below) index + 1 else index, LyricsLine(nextLineId(), 0L, "")) })
    }

    /** Takes back the last change to the lines. */
    fun undo() {
        val revision = undoRevisions.lastOrNull() ?: return
        undoRevisions = undoRevisions.dropLast(1)
        redoRevisions = redoRevisions + currentRevision()
        restore(revision)
    }

    /** Makes again the last change [undo] took back. */
    fun redo() {
        val revision = redoRevisions.lastOrNull() ?: return
        redoRevisions = redoRevisions.dropLast(1)
        undoRevisions = undoRevisions + currentRevision()
        restore(revision)
    }

    /** Plays the track from the line [lineId] is timed at. */
    fun playFrom(lineId: Long) {
        lines.find { it.id == lineId }?.let { playAt(it.timeMs) }
    }

    /**
     * Has [raw] - pasted, found on the web, or written out in full - take the lines' place, each as it comes in
     * rather than as edited; whether it held any lyrics.
     */
    fun replaceLines(raw: String): Boolean {
        val replaced = lyricsLinesOf(raw, ::nextLineId) ?: return false
        edit(replaced, arrivedLines = replaced.associateBy { it.id })
        return true
    }

    /** Has the lyrics written out in full, [text], take the lines' place - none at all where it holds none. */
    fun setLyricsText(text: String) {
        if (!replaceLines(text)) clearLines()
    }

    /** Takes every line out - saved so, the file loses its lyrics. */
    fun clearLines() = edit(emptyList())

    /** Has the lyrics file at [uri] take the lines' place; whether it held any lyrics. */
    suspend fun replaceLinesFromFile(contentResolver: ContentResolver, uri: Uri): Boolean {
        val text = withContext(Dispatchers.IO) {
            runCatching { contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
        }
        return text != null && replaceLines(text)
    }

    /**
     * Looks for the track's lyrics on the web as the tag editor does - every source at once, each set handed on as
     * it arrives, best first.
     */
    fun searchLyrics() {
        val query = lyricsQueryOf(track) ?: return
        val order = OnlineLyrics.order(track.durationMs)
        searchJob?.cancel()
        foundLyrics = emptyList()
        isSearching = true
        searchJob = scope.launch {
            OnlineLyrics.search(query).collect { found -> foundLyrics = (foundLyrics + found).sortedWith(order) }
            isSearching = false
        }
    }

    fun stopSearching() {
        searchJob?.cancel()
        isSearching = false
    }

    /** Has the player play the track from [timeMs] - starting it, on its own, where another is playing. */
    private fun playAt(timeMs: Long) {
        if (playback.value.libraryTrackId == track.id) {
            playbackController.seekTo(timeMs)
            if (!playback.value.isPlaying) playbackController.togglePlayPause()
        } else {
            playbackController.playTracks(listOf(track), startIndex = 0, shuffled = false, startPositionMs = timeMs)
        }
    }

    private fun update(lineId: Long, change: (LyricsLine) -> LyricsLine) = edit(lines.map { if (it.id == lineId) change(it) else it })

    /**
     * Makes [newLines] - and, for lines brought in, [arrivedLines] - the editor's, as one step [undo] can take back.
     * A change that changes nothing is no step; the oldest steps are let go past [MaxRevisions].
     */
    private fun edit(newLines: List<LyricsLine>, arrivedLines: Map<Long, LyricsLine> = this.arrivedLines) {
        if (newLines == lines && arrivedLines == this.arrivedLines) return
        undoRevisions = (undoRevisions + currentRevision()).takeLast(MaxRevisions)
        redoRevisions = emptyList()
        restore(Revision(newLines, arrivedLines))
    }

    private fun currentRevision() = Revision(lines, arrivedLines)

    private fun restore(revision: Revision) {
        arrivedLines = revision.arrivedLines
        lines = revision.lines
    }

    private fun nextLineId(): Long = ++lastLineId

    /** The lines as they stood at one step, and how each came in. */
    private class Revision(val lines: List<LyricsLine>, val arrivedLines: Map<Long, LyricsLine>)

    private companion object {
        /** How many steps back undo reaches. */
        const val MaxRevisions = 100
    }
}
