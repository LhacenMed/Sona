package com.lhacenmed.sona.feature.tageditor.lyrics

import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.attempt
import com.lhacenmed.sona.feature.tageditor.net.string
import org.json.JSONObject

/**
 * BetterLyrics - ArchiveTune's `BetterLyrics`: Apple Music's word-timed lyrics, as TTML, and KuGou's through
 * the same service when Apple has none. Its endpoints are asked in that order, and the first to answer wins.
 */
internal object BetterLyricsSource : LyricsSource {
    private const val Api = "https://lyrics-api.boidu.dev/"
    private val Endpoints = listOf("getLyrics", "kugou/getLyrics")

    override val name = "BetterLyrics"

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> {
        val parameters = buildString {
            append("s=${Http.encode(query.title.trim())}&a=${Http.encode(query.artist.trim())}")
            query.album?.trim()?.takeIf { it.isNotBlank() }?.let { append("&al=${Http.encode(it)}") }
            if (query.durationSeconds > 0) append("&d=${query.durationSeconds}")
        }
        val ttml = Endpoints.firstNotNullOfOrNull { endpoint -> Http.text("$Api$endpoint?$parameters")?.let(::ttmlOf) }
        return listOfNotNull(ttml?.let { SourceLyrics(it, ttmlDurationMs(it)) })
    }
}

/** The TTML a lyrics service answered with: the document itself, or the `ttml` field of a JSON answer. */
internal fun ttmlOf(body: String): String? {
    val trimmed = body.trim()
    val ttml = if (trimmed.startsWith("<")) trimmed else attempt { JSONObject(trimmed).string("ttml") }
    return ttml?.takeIf { it.startsWith("<") }
}
