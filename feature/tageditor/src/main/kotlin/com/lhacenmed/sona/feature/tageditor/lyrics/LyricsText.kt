package com.lhacenmed.sona.feature.tageditor.lyrics

import com.lhacenmed.sona.core.data.lyrics.LyricsEntry
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils
import com.lhacenmed.sona.core.data.lyrics.QRCParser
import com.lhacenmed.sona.core.data.lyrics.WordTimestamp
import java.util.Locale

/** How closely lyrics follow the song: word by word, line by line, or not at all. */
enum class LyricsTiming { WORD, LINE, NONE }

/** Lyrics as they are written into a file, and how closely they follow the song. */
data class LyricsText(val text: String, val timing: LyricsTiming) {
    internal companion object {
        private val WordTime = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>""")

        /**
         * [raw], as a source sent it, in the form a file's lyrics tag holds and every player reads: LRC. TTML
         * and QRC - word-timed formats of their own - become LRC with each word's time before it, so nothing
         * of their timing is lost; LRC and plain text are kept as they are. Null for lyrics with nothing in them.
         */
        fun of(raw: String): LyricsText? {
            val normalized = LyricsUtils.normalizeLyricsText(raw)
            if (!LyricsUtils.hasMeaningfulLyricsContent(normalized)) return null
            val entries = when {
                LyricsUtils.isTtml(normalized) -> LyricsUtils.parseTtml(normalized)
                QRCParser.isQrc(normalized) -> LyricsUtils.parseLyrics(normalized)
                else -> null
            }
            return when {
                entries != null -> entries.takeIf { it.isNotEmpty() }?.let(::fromEntries)
                LyricsUtils.isLineSyncedLrc(normalized) ->
                    LyricsText(normalized, if (WordTime.containsMatchIn(normalized)) LyricsTiming.WORD else LyricsTiming.LINE)
                else -> LyricsText(normalized, LyricsTiming.NONE)
            }
        }

        private fun fromEntries(entries: List<LyricsEntry>): LyricsText {
            var hasWordTiming = false
            val text = entries.joinToString("\n") { entry ->
                val words = entry.words.orEmpty().filterNot { it.isBackground }
                val timedText = words.takeIf { it.isNotEmpty() }?.let { wordTimed(entry.text, it) }
                if (timedText != null) hasWordTiming = true
                "[${timestamp(entry.time)}]${timedText ?: entry.text}"
            }
            return LyricsText(text, if (hasWordTiming) LyricsTiming.WORD else LyricsTiming.LINE)
        }

        /**
         * [line] with each of [words]' times written before it, found in order along the line so the spacing
         * between them stays the line's own - null when a word cannot be found, leaving the line timed whole.
         */
        private fun wordTimed(line: String, words: List<WordTimestamp>): String? {
            val timed = StringBuilder()
            var cursor = 0
            for (word in words) {
                val text = word.text.trim()
                if (text.isEmpty()) continue
                val index = line.indexOf(text, cursor).takeIf { it >= 0 } ?: return null
                timed.append(line, cursor, index).append('<').append(timestamp((word.startTime * 1000).toLong())).append('>').append(text)
                cursor = index + text.length
            }
            return timed.append(line.substring(cursor)).toString()
        }

        /** "mm:ss.xx", as LRC writes a time. */
        private fun timestamp(timeMs: Long): String {
            val safe = timeMs.coerceAtLeast(0L)
            return String.format(Locale.US, "%02d:%02d.%02d", safe / 60_000, safe / 1000 % 60, safe % 1000 / 10)
        }
    }
}
