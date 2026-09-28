package com.lhacenmed.sona.feature.tageditor.lyrics

import android.util.Base64
import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.objects
import com.lhacenmed.sona.feature.tageditor.net.string
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * KuGou - ArchiveTune's `KuGou`: strongest on Chinese, Japanese and Korean releases. Its songs within
 * [DurationToleranceSeconds] of the track are found by name, and each one's lyrics by its hash; then the lyrics
 * found by name. They come back as base64 LRC, trimmed here of the credits KuGou opens and closes with.
 */
internal object KuGouSource : LyricsSource {
    private const val PageSize = 8
    private const val HeadCutLimit = 30
    private const val DurationToleranceSeconds = 8
    private const val MaxResults = 5
    private const val SecondsLimit = 10_000L

    private val AcceptedLine = Regex("""\[(\d\d):(\d\d)\.(\d{2,3})\].*""")

    /** A credit line - "[00:01.00]Composer: …" - rather than a sung one. */
    private val CreditLine = Regex(""".+].+[:：].+""")

    private val Brackets = listOf("""\(.*\)""", "（.*）", "「.*」", "『.*』", "<.*>", "《.*》", "〈.*〉", "＜.*＞").map(::Regex)

    override val name = "KuGou"

    /** [durationMs] is the length of the recording the lyrics were timed to, where KuGou says. */
    private data class Candidate(val id: Long, val accessKey: String, val durationMs: Long?)

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> = coroutineScope {
        candidatesOf(keywordOf(query), query.durationSeconds)
            .map { candidate -> async { download(candidate) } }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun candidatesOf(keyword: String, durationSeconds: Int): List<Candidate> {
        val songs = Http.json(
            "https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=$PageSize&showtype=0&keyword=$keyword",
        )?.optJSONObject("data")?.objects("info").orEmpty()
        val candidates = LinkedHashSet<Candidate>()
        for (song in songs) {
            if (candidates.size >= MaxResults) break
            if (durationSeconds != -1 && abs(song.optInt("duration") - durationSeconds) > DurationToleranceSeconds) continue
            searchLyrics("hash=${song.string("hash")}").firstOrNull()?.let(candidates::add)
        }
        val duration = if (durationSeconds != -1) "&duration=${durationSeconds * 1000}" else ""
        candidates += searchLyrics("keyword=$keyword$duration")
        return candidates.take(MaxResults)
    }

    private suspend fun searchLyrics(by: String): List<Candidate> =
        Http.json("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&$by")
            ?.objects("candidates").orEmpty()
            .map { Candidate(it.optLong("id"), it.string("accesskey"), durationMsOf(it.optLong("duration"))) }
            .filter { it.accessKey.isNotBlank() }

    /**
     * A candidate's length in milliseconds. KuGou gives it in milliseconds for lyrics found by a song's hash, and
     * in seconds for some found by name - and no song runs [SecondsLimit] seconds, so a value under it is seconds.
     */
    private fun durationMsOf(value: Long): Long? = when {
        value <= 0 -> null
        value < SecondsLimit -> value * 1000
        else -> value
    }

    private suspend fun download(candidate: Candidate): SourceLyrics? {
        val content = Http.json(
            "https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=${candidate.id}&accesskey=${candidate.accessKey}",
        )?.string("content")?.takeIf { it.isNotBlank() } ?: return null
        return trimmed(String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8))
            .takeIf { it.isNotBlank() }
            ?.let { SourceLyrics(it, candidate.durationMs) }
    }

    /** "Title - Artist", with what KuGou does not name taken out, encoded with spaces as %20. */
    private fun keywordOf(query: LyricsQuery): String {
        val title = Brackets.fold(query.title) { text, bracket -> bracket.replace(text, "") }
        val artist = query.artist
            .replace(", ", "、").replace(" & ", "、").replace(".", "").replace("和", "、")
            .replace(Regex("""\(.*\)"""), "").replace(Regex("""（.*）"""), "")
        return Http.encode("$title - $artist").replace("+", "%20")
    }

    /** The sung lines alone: the credits before the first and after the last are cut, as ArchiveTune cuts them. */
    private fun trimmed(lrc: String): String {
        val lines = lrc.replace("&apos;", "'").lines().filter { it.matches(AcceptedLine) }
        if (lines.isEmpty()) return ""
        var headCut = 0
        for (i in min(HeadCutLimit, lines.lastIndex) downTo 0) {
            if (lines[i].matches(CreditLine)) {
                headCut = i + 1
                break
            }
        }
        var tailCut = 0
        for (i in min(lines.size - HeadCutLimit, lines.lastIndex) downTo 0) {
            if (lines[lines.lastIndex - i].matches(CreditLine)) {
                tailCut = i + 1
                break
            }
        }
        return lines.drop(headCut).dropLast(tailCut).joinToString("\n")
    }
}
