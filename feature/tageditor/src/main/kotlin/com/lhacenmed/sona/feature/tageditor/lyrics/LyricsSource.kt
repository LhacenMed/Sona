package com.lhacenmed.sona.feature.tageditor.lyrics

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
