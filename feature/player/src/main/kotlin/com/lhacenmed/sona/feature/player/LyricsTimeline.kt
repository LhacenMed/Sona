package com.lhacenmed.sona.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.lyrics.LyricsEntry
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.findCurrentLineIndex
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.insertInstrumentalBreaks
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.isLineSyncedLrc
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.isTtml
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.parseLyrics
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.parseTtml
import com.lhacenmed.sona.core.model.Track

/** Lead time offset for LRC-style line-synced lyrics (ms). */
private const val LRC_LEAD_MS = 300L

/** Lead time offset for TTML word-synced lyrics (ms). */
private const val TTML_LEAD_MS = 0L

private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L

/** Sentinel entry prepended so auto-scroll has headroom above the first line. */
internal val HEAD_LYRICS_ENTRY = LyricsEntry(time = 0L, text = "")

/**
 * A track's lyrics read once for everything the player shows of them - the line in the lyrics pill and the
 * full lyrics it opens into, or the lyrics sheet - so all agree on which line is playing.
 *
 * Synced lyrics start with [HEAD_LYRICS_ENTRY]; plain ones are their lines, untimed.
 */
@Immutable
internal class LyricsTimeline(
    val entries: List<LyricsEntry>,
    val isSynced: Boolean,
    val isWordSynced: Boolean,
) {
    private val leadMs = if (isWordSynced) TTML_LEAD_MS else LRC_LEAD_MS

    /** Where the lyrics are at [positionMs] of the track, moved by the user's [syncOffsetMs]. */
    fun lyricsPositionMs(positionMs: Long, syncOffsetMs: Int): Long = (positionMs + syncOffsetMs).coerceAtLeast(0L)

    /** Where the highlight is drawn at [lyricsPositionMs]: a little ahead, so a line lights as it is sung. */
    fun highlightPositionMs(lyricsPositionMs: Long): Long = lyricsPositionMs + leadMs + LYRIC_VISUAL_TUNING_OFFSET_MS

    /** The line playing at [lyricsPositionMs]. */
    fun lineIndexAt(lyricsPositionMs: Long): Int = findCurrentLineIndex(entries, highlightPositionMs(lyricsPositionMs), 0L)

    companion object {
        /** [lyrics] as a track's file holds them - blank where it holds none, which reads as no lines. */
        fun parse(lyrics: String, durationMs: Long): LyricsTimeline {
            if (lyrics.isBlank()) return LyricsTimeline(emptyList(), isSynced = false, isWordSynced = false)
            val isWordSynced = isTtml(lyrics)
            val isSynced = isWordSynced || isLineSyncedLrc(lyrics)
            val parsed =
                when {
                    isWordSynced -> parseTtml(lyrics)
                    isSynced -> insertInstrumentalBreaks(parseLyrics(lyrics), durationMs.coerceAtLeast(0L))
                    else -> lyrics.lines().filter { it.isNotBlank() }.map { line -> LyricsEntry(time = -1L, text = line.trim()) }
                }
            val entries = if (parsed.isNotEmpty() && parsed.first().time >= 0) listOf(HEAD_LYRICS_ENTRY) + parsed else parsed
            return LyricsTimeline(entries, isSynced, isWordSynced)
        }
    }
}

/**
 * [track]'s lyrics as a timeline: null while its file is being read. Keyed by the whole track, so it is read again
 * as the library reads it anew.
 */
@Composable
internal fun rememberLyricsTimeline(
    track: Track,
    durationMs: Long,
    lyricsViewModel: LyricsViewModel,
): LyricsTimeline? {
    val lyrics by remember(track) { lyricsViewModel.lyrics(track) }.collectAsStateWithLifecycle(initialValue = null)
    return remember(lyrics, durationMs) { lyrics?.let { LyricsTimeline.parse(it, durationMs) } }
}
