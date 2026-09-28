package com.lhacenmed.sona.feature.tageditor.lyrics

import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.attempt
import com.lhacenmed.sona.feature.tageditor.net.objects
import com.lhacenmed.sona.feature.tageditor.net.string
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Musixmatch - syncedlyrics' `Musixmatch`, in its enhanced mode: its desktop app's API, asked with a user token
 * it hands out, for the best matching song's lyrics timed word by word, or else line by line.
 *
 * The token is kept for ten minutes, as syncedlyrics keeps it. Musixmatch hands out a token of zeros where it
 * will not serve the asker - a network it has flagged - and answers searches under it with unrelated songs, so
 * that token is taken as no answer at all rather than trusted.
 */
internal object MusixmatchSource : LyricsSource {
    private const val Api = "https://apic-desktop.musixmatch.com/ws/1.1/"
    private const val AppId = "web-desktop-app-v1.0"
    private const val TokenLifetimeMillis = 10 * 60 * 1000L

    override val name = "Musixmatch"

    private val tokenLock = Mutex()
    private var token: String? = null
    private var tokenExpiresAt = 0L

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> {
        val token = token() ?: return emptyList()
        val tracks = get("track.search", token, "q" to query.searchTerm, "page_size" to "5", "page" to "1")
            ?.optJSONObject("body")
            ?.objects("track_list")
            .orEmpty()
            .mapNotNull { it.optJSONObject("track") }
        val track = query.bestMatches(
            tracks,
            title = { it.string("track_name") },
            artist = { it.string("artist_name") },
            durationMs = { it.optLong("track_length").takeIf { seconds -> seconds > 0 }?.times(1000) },
        ).firstOrNull() ?: return emptyList()

        val trackId = track.string("track_id")
        val lyrics = wordByWord(trackId, token) ?: lineByLine(trackId, token) ?: return emptyList()
        return listOf(SourceLyrics(lyrics, track.optLong("track_length").takeIf { it > 0 }?.times(1000)))
    }

    /** The user token, asked for again once it is ten minutes old - null where Musixmatch will not serve this device. */
    private suspend fun token(): String? = tokenLock.withLock {
        val now = System.currentTimeMillis()
        token?.takeIf { now < tokenExpiresAt }?.let { return@withLock it }
        val fresh = get("token.get", token = null, "user_language" to "en")
            ?.optJSONObject("body")
            ?.string("user_token")
            ?.takeIf { it.isNotBlank() && it.any { char -> char != '0' } }
            ?: return@withLock null
        token = fresh
        tokenExpiresAt = now + TokenLifetimeMillis
        fresh
    }

    /** syncedlyrics' `get_lrc_word_by_word`: the rich sync, each word's time before it. */
    private suspend fun wordByWord(trackId: String, token: String): String? {
        val message = get("track.richsync.get", token, "track_id" to trackId) ?: return null
        if (message.optJSONObject("header")?.optInt("status_code") != 200) return null
        val body = message.optJSONObject("body")?.optJSONObject("richsync")?.string("richsync_body") ?: return null
        val lines = attempt { JSONArray(body).objects() } ?: return null
        return buildString {
            for (line in lines) {
                val start = line.optDouble("ts")
                append('[').append(clockTime(start)).append("] ")
                for (word in line.objects("l")) {
                    append('<').append(clockTime(start + word.optDouble("o"))).append("> ").append(word.optString("c")).append(' ')
                }
                append('\n')
            }
        }.takeIf { it.isNotBlank() }
    }

    /** syncedlyrics' `get_lrc_by_id`: the subtitle, as LRC. */
    private suspend fun lineByLine(trackId: String, token: String): String? =
        get("track.subtitle.get", token, "track_id" to trackId, "subtitle_format" to "lrc")
            ?.optJSONObject("body")
            ?.optJSONObject("subtitle")
            ?.string("subtitle_body")
            ?.takeIf { it.isNotBlank() }

    /** One call to the API - its `message` - with the app id, the token and a timestamp, as syncedlyrics' `_get` sends. */
    private suspend fun get(action: String, token: String?, vararg query: Pair<String, String>): JSONObject? {
        val parameters = buildList {
            addAll(query)
            add("app_id" to AppId)
            token?.let { add("usertoken" to it) }
            add("t" to System.currentTimeMillis().toString())
        }.joinToString("&") { (key, value) -> "$key=${Http.encode(value)}" }
        return Http.json("$Api$action?$parameters")?.optJSONObject("message")
    }

    /** syncedlyrics' `format_time`: seconds as "mm:ss.xx". */
    private fun clockTime(seconds: Double): String {
        val centiseconds = (seconds * 100).toLong().coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d.%02d", centiseconds / 6000, centiseconds / 100 % 60, centiseconds % 100)
    }
}
