package com.lhacenmed.sona.feature.tageditor.lyrics

import com.lhacenmed.sona.feature.tageditor.lookup.TrackMatcher
import com.lhacenmed.sona.feature.tageditor.lookup.TrackQuery
import kotlin.math.abs

/** The song whose lyrics are looked for. [durationSeconds] is -1 when it is not known. */
internal data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int,
)

/**
 * One set of lyrics as a source publishes it - LRC, TTML or plain text - and how long the recording it was
 * timed to is, where the source says: the same song is released at more than one length, and synced lyrics
 * only fit the one they were timed to.
 */
internal data class SourceLyrics(val text: String, val durationMs: Long?)

/**
 * A source of lyrics - ArchiveTune's `LyricsProvider`, for the ones that find a song by its names and length
 * rather than by a video it was published with.
 */
internal interface LyricsSource {
    val name: String

    /** Every set of lyrics the source has for [query], its likeliest first - ArchiveTune's `getAllLyrics`. */
    suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics>
}

/** What a source that takes one line is searched with - syncedlyrics' "<title> <artist>". */
internal val LyricsQuery.searchTerm: String
    get() = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")

/** Below this likeness - syncedlyrics' 65 - a song a source found is taken for another song. */
private const val MinLikeness = 0.65

/**
 * The songs among [candidates] that answer this query, best first - syncedlyrics' `get_best_match`, keeping
 * every one that passes rather than the first: at least [MinLikeness] alike by name, by the tag lookup's own
 * measure, and among them the likest names, then the length nearest the track's.
 */
internal fun <T> LyricsQuery.bestMatches(
    candidates: List<T>,
    title: (T) -> String,
    artist: (T) -> String,
    durationMs: (T) -> Long?,
): List<T> {
    val query = TrackQuery(artist = this.artist, title = this.title, version = TrackMatcher.versionOf(this.title))
    val trackMs = durationSeconds.takeIf { it > 0 }?.times(1000L)
    return candidates
        .map { it to TrackMatcher.score(query, title(it), artist(it)) }
        .filter { (_, likeness) -> likeness >= MinLikeness }
        .sortedWith(
            compareByDescending<Pair<T, Double>> { (_, likeness) -> likeness }
                .thenBy { (candidate, _) -> durationMs(candidate)?.let { ms -> trackMs?.let { abs(ms - it) } } ?: Long.MAX_VALUE },
        )
        .map { (candidate, _) -> candidate }
}

private val ClockTime = Regex("""^(?:(\d+):)?(?:(\d+):)?(\d+(?:\.\d+)?)s?$""")
private val TtmlBodyDuration = Regex("""<body\b[^>]*\bdur="([^"]+)"""")

/**
 * A length as the sources write it - "3:21.570", "1:02:03.5", "201.57" or "201.57s" - in milliseconds, or
 * null for none that can be read.
 */
internal fun clockTimeMs(text: String): Long? {
    val match = ClockTime.matchEntire(text.trim()) ?: return null
    val (first, second, seconds) = match.destructured
    // One colon is minutes and seconds; two are hours, minutes and seconds.
    val hours = if (second.isNotEmpty()) first.toLong() else 0L
    val minutes = if (second.isNotEmpty()) second.toLong() else first.toLongOrNull() ?: 0L
    return ((hours * 3600 + minutes * 60) * 1000 + seconds.toDouble() * 1000).toLong().takeIf { it > 0 }
}

/** The length of the recording a TTML document was timed to - Apple's `<body dur="…">` - or null where it has none. */
internal fun ttmlDurationMs(ttml: String): Long? = TtmlBodyDuration.find(ttml)?.groupValues?.get(1)?.let(::clockTimeMs)
