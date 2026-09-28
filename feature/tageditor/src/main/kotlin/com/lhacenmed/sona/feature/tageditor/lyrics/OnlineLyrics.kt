package com.lhacenmed.sona.feature.tageditor.lyrics

import com.lhacenmed.sona.feature.tageditor.net.attempt
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** How near a recording's length has to be to the track's for lyrics timed to it to fit - LrcLib's own margin. */
private const val FittingDurationMarginMs = 2_000L

/**
 * Lyrics one of the [OnlineLyrics] sources found, as a file holds them, which source it was, and the length of
 * the recording they were timed to where the source says - the same song is released at more than one length,
 * and synced lyrics only follow the one they were timed to.
 */
data class FoundLyrics(val lyrics: LyricsText, val source: String, val durationMs: Long?) {

    /**
     * How much longer - or, below zero, shorter - the recording these lyrics were timed to is than a track
     * [trackDurationMs] long; null where the source did not say.
     */
    fun durationDifferenceMs(trackDurationMs: Long): Long? = durationMs?.let { it - trackDurationMs }

    /** Whether these lyrics were timed to a recording as long as a track [trackDurationMs] long. */
    fun fits(trackDurationMs: Long): Boolean =
        durationDifferenceMs(trackDurationMs)?.let { abs(it) <= FittingDurationMarginMs } == true
}

/**
 * A song's lyrics from the web - ArchiveTune's `LyricsHelper.getAllLyrics`, over the sources that find a song
 * by its names: every source asked at once, and every set of lyrics each finds handed on the moment it arrives,
 * so the first are on screen while the slowest source is still answering.
 */
internal object OnlineLyrics {
    /**
     * In the order that decides between two alike answers: ArchiveTune's, then syncedlyrics' own - NetEase and
     * Musixmatch, and Genius last, as it has words but never their timing. Adding a source is adding it here.
     */
    private val sources = listOf(
        BetterLyricsSource,
        BetterLyricsPortatoSource,
        YouLyPlusSource,
        LrcLibSource,
        KuGouSource,
        SimpMusicSource,
        UnisonSource,
        NetEaseSource,
        MusixmatchSource,
        GeniusSource,
    )

    val sourceNames: List<String> = sources.map { it.name }

    /** Every set of lyrics any source has for [query], each once, as it is found; the flow ends when all have answered. */
    fun search(query: LyricsQuery): Flow<FoundLyrics> = channelFlow {
        sources.forEach { source ->
            launch(Dispatchers.IO) {
                attempt { source.fetchAll(query) }.orEmpty()
                    .mapNotNull { found -> LyricsText.of(found.text)?.let { FoundLyrics(it, source.name, found.durationMs) } }
                    .distinctBy { it.lyrics.text }
                    .forEach { send(it) }
            }
        }
    }

    /**
     * The order lyrics are offered in, for a track [trackDurationMs] long: word-timed before line-timed before
     * plain, the way ArchiveTune picks; then those timed to a recording of the track's length, before those of
     * an unknown length, before those of another; then the earlier source first.
     */
    fun order(trackDurationMs: Long): Comparator<FoundLyrics> =
        compareBy<FoundLyrics>(
            { it.lyrics.timing.ordinal },
            {
                when {
                    it.fits(trackDurationMs) -> 0
                    it.durationMs == null -> 1
                    else -> 2
                }
            },
            { sourceNames.indexOf(it.source) },
        )
}
