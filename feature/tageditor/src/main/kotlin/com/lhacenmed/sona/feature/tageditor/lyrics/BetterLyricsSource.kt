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
    override val name = "BetterLyrics"

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> =
        BetterLyricsApi.fetch(query, "getLyrics", "kugou/getLyrics")
}

/**
 * BetterLyrics Portato - ArchiveTune's `BetterLyricsPortatoProvider`: QQ Music's word-timed lyrics, through
 * BetterLyrics' own service. Strongest on Chinese releases.
 */
internal object BetterLyricsPortatoSource : LyricsSource {
    override val name = "BetterLyrics Portato"

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> =
        BetterLyricsApi.fetch(query, "qq/getLyrics")
}

/** The service both BetterLyrics sources ask - ArchiveTune's `BetterLyrics.fetchLyrics`. */
private object BetterLyricsApi {
    private const val Api = "https://lyrics-api.boidu.dev/"

    /** The lyrics the first of [endpoints] to have them answers with, and the length of their recording where they say. */
    suspend fun fetch(query: LyricsQuery, vararg endpoints: String): List<SourceLyrics> {
        if (query.title.isBlank() || query.artist.isBlank()) return emptyList()
        val parameters = buildString {
            append("s=${Http.encode(query.title.trim())}&a=${Http.encode(query.artist.trim())}")
            query.album?.trim()?.takeIf { it.isNotBlank() }?.let { append("&al=${Http.encode(it)}") }
            if (query.durationSeconds > 0) append("&d=${query.durationSeconds}")
        }
        val lyrics = endpoints.firstNotNullOfOrNull { endpoint -> Http.text("$Api$endpoint?$parameters")?.let(::lyricsOf) }
        return listOfNotNull(lyrics?.let { SourceLyrics(it, ttmlDurationMs(it)) })
    }

    /** ArchiveTune's `decodeLyrics`: a document answered as it is, or else the `ttml` field of a JSON answer. */
    private fun lyricsOf(body: String): String? {
        val trimmed = body.trim()
        val lyrics = if (trimmed.startsWith("<")) trimmed else attempt { JSONObject(trimmed).string("ttml") }
        return lyrics?.takeIf { it.isNotBlank() }
    }
}
