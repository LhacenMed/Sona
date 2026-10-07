package com.lhacenmed.sona.feature.tageditor.lyrics

import com.lhacenmed.sona.feature.tageditor.lookup.TrackMatcher
import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.objects
import com.lhacenmed.sona.feature.tageditor.net.string
import kotlin.math.abs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * SimpMusic - ArchiveTune's `SimpMusicLyrics`: lyrics its listeners contribute for YouTube's recordings, each
 * with that recording's length, and timed word by word where they could be.
 *
 * ArchiveTune asks by the video it is playing; a track on this device has none, so its videos are found by
 * name first - through the same API's search - and the likest are asked for as ArchiveTune asks. Each gives its
 * word-timed lyrics, or its line-timed ones, or its plain ones - the closest it has - within five seconds of the
 * track's length where that is known, as ArchiveTune's `getAllLyrics` holds them to.
 *
 * Its lyrics come HTML-escaped - "you&#x27;re" - so they are decoded as they arrive, before anything shows or
 * saves them.
 */
internal object SimpMusicSource : LyricsSource {
    private const val Api = "https://api-lyrics.simpmusic.org/v1/"
    private val Headers = mapOf("Accept" to "application/json", "User-Agent" to "SimpMusicLyrics/1.0")
    private const val MaxVideos = 3
    private const val MaxDurationDeltaSeconds = 5

    /** A character reference - by number, decimal or hex - or one of the names HTML escapes text with. */
    private val HtmlEntity = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|amp|quot|apos|lt|gt);")

    override val name = "SimpMusic"

    private class Video(val id: String, val title: String, val artist: String, val durationMs: Long?)

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> = coroutineScope {
        val videos = Http.json("${Api}search?q=${Http.encode(query.searchTerm)}", Headers)
            ?.takeIf { it.isSuccess() }
            ?.objects("data")
            .orEmpty()
            .map { Video(it.string("videoId"), it.string("songTitle"), artistOf(it, query), it.durationMs()) }
            .filter { it.id.isNotBlank() && it.fits(query) }
        query.bestMatches(videos, Video::title, Video::artist, Video::durationMs)
            .take(MaxVideos)
            .map { video -> async { lyricsOf(video, query) } }
            .awaitAll()
            .filterNotNull()
    }

    /** ArchiveTune's `getLyrics` for one video: of the lyrics it has, those nearest the track's length. */
    private suspend fun lyricsOf(video: Video, query: LyricsQuery): SourceLyrics? {
        val tracks = Http.json("$Api${video.id}", Headers)?.takeIf { it.isSuccess() }?.objects("data").orEmpty()
        val best = if (query.durationSeconds > 0) {
            tracks.minByOrNull { abs(it.optInt("durationSeconds") - query.durationSeconds) }
        } else {
            tracks.firstOrNull()
        } ?: return null
        val lyrics = listOf("richSyncLyrics", "syncedLyrics", "plainLyric")
            .firstNotNullOfOrNull { key -> best.string(key).takeIf { it.isNotBlank() } }
            ?: return null
        return SourceLyrics(decodeHtmlEntities(lyrics), best.durationMs())
    }

    /** [text] with every [HtmlEntity] turned back into the character it stands for; anything else is left as it is. */
    private fun decodeHtmlEntities(text: String): String = text.replace(HtmlEntity) { match ->
        val entity = match.groupValues[1]
        val codePoint = when {
            entity.startsWith("#x", ignoreCase = true) -> entity.substring(2).toIntOrNull(16)
            entity.startsWith("#") -> entity.substring(1).toIntOrNull()
            else -> null
        }
        when {
            codePoint != null -> if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else match.value
            entity == "amp" -> "&"
            entity == "quot" -> "\""
            entity == "apos" -> "'"
            entity == "lt" -> "<"
            entity == "gt" -> ">"
            else -> match.value
        }
    }

    /**
     * Who a video is by. Its `artistName` is often the channel that uploaded it - "MusicOnly" - while its title
     * names the artist - "The Weeknd - Blinding Lights (Lyrics)" - so a title naming [query]'s artist makes it
     * that artist's; a cover or a live take is still told apart, by the version its title names.
     */
    private fun artistOf(video: JSONObject, query: LyricsQuery): String {
        val artist = TrackMatcher.normalize(query.artist)
        val namesArtist = artist.isNotBlank() && artist in TrackMatcher.normalize(video.string("songTitle"))
        return if (namesArtist) query.artist else video.string("artistName")
    }

    private fun Video.fits(query: LyricsQuery): Boolean =
        query.durationSeconds <= 0 || durationMs == null ||
            abs(durationMs / 1000 - query.durationSeconds) <= MaxDurationDeltaSeconds

    private fun JSONObject.isSuccess(): Boolean = string("type") == "success"

    private fun JSONObject.durationMs(): Long? = optLong("durationSeconds").takeIf { it > 0 }?.times(1000)
}
