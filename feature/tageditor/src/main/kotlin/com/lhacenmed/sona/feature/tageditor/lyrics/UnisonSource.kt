package com.lhacenmed.sona.feature.tageditor.lyrics

import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.objects
import com.lhacenmed.sona.feature.tageditor.net.string
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Unison - ArchiveTune's `Unison`: lyrics its listeners submit and vote on, searched by the song's names and
 * length, best voted first. A result that came back without its lyrics is asked for again on its own, all of
 * them at once.
 */
internal object UnisonSource : LyricsSource {
    private const val Api = "https://unison.boidu.dev/"
    private const val SearchLimit = 5

    override val name = "Unison"

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> = coroutineScope {
        if (query.title.isBlank() || query.artist.isBlank()) return@coroutineScope emptyList()
        val url = buildString {
            append("${Api}lyrics/search?song=${Http.encode(query.title.trim())}&artist=${Http.encode(query.artist.trim())}")
            query.album?.trim()?.takeIf { it.isNotBlank() }?.let { append("&album=${Http.encode(it)}") }
            if (query.durationSeconds > 0) append("&duration=${query.durationSeconds}")
            append("&limit=$SearchLimit")
        }
        val results = Http.json(url)?.takeIf { it.optBoolean("success") }?.objects("data").orEmpty()
        results.map { result ->
            async {
                val lyrics = result.string("lyrics").takeIf { it.isNotBlank() }
                    ?: result.string("videoId").takeIf { it.isNotBlank() }?.let { entryLyrics("lyrics?v=${Http.encode(it)}") }
                    ?: entryLyrics("lyrics/${result.optLong("id")}")
                // Each result names the length of the recording its lyrics were timed to, in seconds.
                lyrics?.let { SourceLyrics(it, (result.optDouble("duration") * 1000).toLong().takeIf { ms -> ms > 0 }) }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun entryLyrics(path: String): String? =
        Http.json(Api + path)
            ?.takeIf { it.optBoolean("success") }
            ?.optJSONObject("data")
            ?.lyrics()

    private fun JSONObject.lyrics(): String? = string("lyrics").takeIf { it.isNotBlank() }
}
