package com.lhacenmed.sona.feature.tageditor.lyricseditor

import androidx.compose.runtime.Immutable
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils
import com.lhacenmed.sona.feature.tageditor.lyrics.LyricsText
import java.util.Locale

/** One line of lyrics as the editor holds it: the time into the track it is sung at, and its words. */
@Immutable
data class LyricsLine(
    /** Which line it is, kept as its time and words change and as lines are added around it. */
    val id: Long,
    val timeMs: Long,
    val text: String,
)

/** An LRC info tag - `[ar:Artist]`, `[offset:+120]` - which tells about the lyrics rather than being one of their lines. */
private val InfoTag = Regex("""^\[[A-Za-z#]+:.*]$""")

/**
 * [raw] - lyrics from the store, the clipboard, a file or the web - as the editor's lines, in time order: each timed
 * line at its time, once for each time it is stamped with, and every untimed one at the start, keeping the order
 * it is sung in - as plain lyrics pasted in are, waiting to be timed. Null where [raw] holds no lyrics.
 *
 * Lines are what the editor times, so lyrics timed word by word keep their lines' times and lose their words'.
 */
internal fun lyricsLinesOf(raw: String, nextId: () -> Long): List<LyricsLine>? {
    // As a file holds them: TTML and QRC become LRC, and LRC and plain text stay as they are.
    val lyrics = LyricsText.of(raw)?.text ?: return null
    return lyrics.lines()
        .flatMap { line ->
            val trimmed = line.trim()
            LyricsUtils.parseSyncedLine(trimmed)?.map { LyricsLine(nextId(), it.time, it.text) }
                ?: if (trimmed.isEmpty() || InfoTag.matches(trimmed)) emptyList() else listOf(LyricsLine(nextId(), 0L, trimmed))
        }
        .sortedBy { it.timeMs }
        .takeIf { it.isNotEmpty() }
}

/**
 * [lines] as lyrics are kept: LRC, each line at its time, in time order - or, while not one of them is timed yet,
 * plain lines, which show as lyrics that do not follow the song rather than as every line at once.
 */
internal fun lyricsTextOf(lines: List<LyricsLine>): String {
    val ordered = lines.sortedBy { it.timeMs }
    if (ordered.all { it.timeMs == 0L }) return ordered.joinToString("\n") { it.text }
    return ordered.joinToString("\n") { "[${lrcTimeOf(it.timeMs)}]${it.text}" }
}

/** [timeMs] as LRC writes a time, to the millisecond - "01:02.345" - which every player reads. */
private fun lrcTimeOf(timeMs: Long): String =
    "%02d:%02d.%03d".format(Locale.ROOT, timeMs / 60_000, timeMs / 1_000 % 60, timeMs % 1_000)

/**
 * The parts of a line's time the editor shows, takes typed in, and nudges each by its own step - as
 * "01 : 02 . 345" reads: [unitMs] long each, from none to [maxValue], shown in [digits] digits.
 */
internal enum class LyricsTimePart(val unitMs: Long, val stepMs: Long, val maxValue: Long, val digits: Int) {
    MINUTES(unitMs = 60_000, stepMs = 60_000, maxValue = 99, digits = 2),
    SECONDS(unitMs = 1_000, stepMs = 1_000, maxValue = 59, digits = 2),

    /** Nudged ten at a time: a millisecond is finer than a finger can hear. */
    MILLISECONDS(unitMs = 1, stepMs = 10, maxValue = 999, digits = 3),
    ;

    /** This part of [timeMs]. */
    fun valueOf(timeMs: Long): Long =
        when (this) {
            MINUTES -> timeMs / 60_000
            SECONDS -> timeMs / 1_000 % 60
            MILLISECONDS -> timeMs % 1_000
        }

    /** This part of [timeMs], as the editor shows it. */
    fun of(timeMs: Long): String = valueOf(timeMs).toString().padStart(digits, '0')

    /** [timeMs] with this part set to [value] - held within what the part can be - and the others as they were. */
    fun withValue(timeMs: Long, value: Long): Long = timeMs + (value.coerceIn(0, maxValue) - valueOf(timeMs)) * unitMs
}

/** The line [positionMs] into the track is on: the one timed latest at or before it - the first of those, on a tie. */
internal fun List<LyricsLine>.lineAt(positionMs: Long): LyricsLine? =
    filter { it.timeMs <= positionMs }.maxByOrNull { it.timeMs }
