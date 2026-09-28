package com.lhacenmed.sona.feature.tageditor.lyrics

import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.objects
import com.lhacenmed.sona.feature.tageditor.net.string
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * NetEase Cloud Music (music.163.com) - syncedlyrics' `NetEase`: its PC client's search, then each song's LRC.
 * Strong on Chinese releases, and broad beyond them.
 *
 * Asked as the PC client asks, with the session cookie syncedlyrics carries: without it the search is refused
 * with a verification demand. Each song gives its length, in milliseconds.
 */
internal object NetEaseSource : LyricsSource {
    private const val SearchApi = "https://music.163.com/api/search/pc"
    private const val LyricApi = "https://music.163.com/api/song/lyric"
    private const val MaxResults = 3

    private val Headers = mapOf(
        "cookie" to "NMTID=00OAVK3xqDG726ITU6jopU6jF2yMk0AAAGCO8l1BA; JSESSIONID-WYYY=8KQo11YK2GZP45RMlz8Kn80vHZ9%2FGvwzRKQXXy0iQoFKycWdBlQjbfT0MJrFa6hwRfmpfBYKeHliUPH287JC3hNW99WQjrh9b9RmKT%2Fg1Exc2VwHZcsqi7ITxQgfEiee50po28x5xTTZXKoP%2FRMctN2jpDeg57kdZrXz%2FD%2FWghb%5C4DuZ%3A1659124633932; _iuqxldmzr_=32; _ntes_nnid=0db6667097883aa9596ecfe7f188c3ec,1659122833973; _ntes_nuid=0db6667097883aa9596ecfe7f188c3ec; WNMCID=xygast.1659122837568.01.0; WEVNSM=1.0.0; WM_NI=CwbjWAFbcIzPX3dsLP%2F52VB%2Bxr572gmqAYwvN9KU5X5f1nRzBYl0SNf%2BV9FTmmYZy%2FoJLADaZS0Q8TrKfNSBNOt0HLB8rRJh9DsvMOT7%2BCGCQLbvlWAcJBJeXb1P8yZ3RHA%3D; WM_NIKE=9ca17ae2e6ffcda170e2e6ee90c65b85ae87b9aa5483ef8ab3d14a939e9a83c459959caeadce47e991fbaee82af0fea7c3b92a81a9ae8bd64b86beadaaf95c9cedac94cf5cedebfeb7c121bcaefbd8b16dafaf8fbaf67e8ee785b6b854f7baff8fd1728287a4d1d246a6f59adac560afb397bbfc25ad9684a2c76b9a8d00b2bb60b295aaafd24a8e91bcd1cb4882e8beb3c964fb9cbd97d04598e9e5a4c6499394ae97ef5d83bd86a3c96f9cbeffb1bb739aed9ea9c437e2a3; WM_TID=AAkRFnl03RdABEBEQFOBWHCPOeMra4IL; playerid=94262567",
    )

    override val name = "NetEase"

    private class Song(val id: Long, val title: String, val artist: String, val durationMs: Long?)

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> = coroutineScope {
        val songs = Http.json("$SearchApi?limit=10&type=1&offset=0&s=${Http.encode(query.searchTerm)}", Headers)
            ?.optJSONObject("result")
            ?.objects("songs")
            .orEmpty()
            .map { song ->
                Song(
                    id = song.optLong("id"),
                    title = song.string("name"),
                    artist = song.objects("artists").firstOrNull()?.string("name").orEmpty(),
                    durationMs = song.optLong("duration").takeIf { it > 0 },
                )
            }
        query.bestMatches(songs, Song::title, Song::artist, Song::durationMs)
            .take(MaxResults)
            .map { song -> async { lyricsOf(song) } }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun lyricsOf(song: Song): SourceLyrics? =
        Http.json("$LyricApi?id=${song.id}&lv=1", Headers)
            ?.optJSONObject("lrc")
            ?.string("lyric")
            ?.takeIf { it.isNotBlank() }
            ?.let { SourceLyrics(it, song.durationMs) }
}
