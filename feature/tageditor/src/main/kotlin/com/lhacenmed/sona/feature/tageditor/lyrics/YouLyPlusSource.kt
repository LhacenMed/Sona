package com.lhacenmed.sona.feature.tageditor.lyrics

import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.attempt
import com.lhacenmed.sona.feature.tageditor.net.objects
import com.lhacenmed.sona.feature.tageditor.net.string
import java.util.Locale
import org.json.JSONObject

/**
 * YouLyPlus - ArchiveTune's `YouLyPlus`: word-timed lyrics as TTML, or else as timed lines it sends as JSON,
 * written here as LRC. It is served from several mirrors, asked in turn until one answers.
 */
internal object YouLyPlusSource : LyricsSource {
    private val Mirrors = listOf(
        "https://lyricsplus.binimum.org/",
        "https://lyricsplus.prjktla.my.id/",
        "https://lyricsplus.prjktla.workers.dev/",
        "https://lyricsplus.atomix.one/",
        "https://lyricsplus-seven.vercel.app/",
    )
    private val Headers = mapOf("Accept" to "application/json")

    override val name = "YouLyPlus"

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> {
        if (query.title.isBlank() || query.artist.isBlank()) return emptyList()
        val parameters = buildString {
            append("title=${Http.encode(query.title.trim())}&artist=${Http.encode(query.artist.trim())}")
            query.album?.trim()?.takeIf { it.isNotBlank() }?.let { append("&album=${Http.encode(it)}") }
            if (query.durationSeconds > 0) append("&duration=${query.durationSeconds}")
        }
        return listOfNotNull(
            fromMirrors("v1/ttml/get?$parameters") { body -> ttmlOf(body)?.let { SourceLyrics(it, ttmlDurationMs(it)) } }
                ?: fromMirrors("v2/lyrics/get?$parameters") { body -> attempt { lyricsOf(JSONObject(body)) } },
        )
    }

    private suspend fun fromMirrors(path: String, decode: (String) -> SourceLyrics?): SourceLyrics? =
        Mirrors.firstNotNullOfOrNull { mirror -> Http.text(mirror + path, Headers)?.let(decode)?.takeIf { it.text.isNotBlank() } }

    /** The lines as LRC, with the length of the recording they were timed to - its `totalDuration`, "3:20.046". */
    private fun lyricsOf(response: JSONObject): SourceLyrics? =
        lrcOf(response)?.let { SourceLyrics(it, response.optJSONObject("metadata")?.string("totalDuration")?.let(::clockTimeMs)) }

    /** Timed lines as LRC - each word's time before it, where the lines are timed word by word. */
    private fun lrcOf(response: JSONObject): String? {
        val lines = response.objects("lyrics")
        if (lines.isEmpty()) return null
        val isWordTimed = response.string("type").equals("Word", ignoreCase = true)
        val timedLines = lines.filter { it.has("time") && !it.isNull("time") }
        if (timedLines.isEmpty()) {
            return lines.map { it.string("text") }.filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotBlank() }
        }
        return timedLines.joinToString("\n") { line ->
            buildString {
                append('[').append(timestamp(line.optLong("time"))).append(']')
                val syllables = line.objects("syllabus").filter { it.string("text").isNotBlank() && it.has("time") }
                if (isWordTimed && syllables.isNotEmpty()) {
                    syllables.forEach { append('<').append(timestamp(it.optLong("time"))).append('>').append(it.optString("text")) }
                } else {
                    append(line.string("text"))
                }
            }
        }.takeIf { it.isNotBlank() }
    }

    private fun timestamp(timeMs: Long): String {
        val safe = timeMs.coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d.%03d", safe / 60_000, safe / 1000 % 60, safe % 1000)
    }
}
