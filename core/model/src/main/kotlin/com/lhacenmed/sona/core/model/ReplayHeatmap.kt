package com.lhacenmed.sona.core.model

import java.nio.ByteBuffer

/**
 * How often each moment of one video has been watched - YouTube's Most replayed, from this device's own watching. The video
 * is cut into equal moments of about [MomentMs] each, every one holding how long it has been played in all: a moment watched
 * twice holds twice its length. Only playing adds to it - a scrub or a seek passes over nothing - and in the video's own time,
 * so a moment watched at double speed counts once, as one watched at normal speed does.
 *
 * Moments short enough for a scene replayed once to stand out in a film as in a clip, and drawn as [PointCount] points
 * whatever their number. Made for [durationMs]: a file of another length is another video.
 */
class ReplayHeatmap private constructor(val durationMs: Long, private val playedMs: IntArray) {

    private val momentCount get() = playedMs.size

    /** This, with the stretch from [fromMs] to [toMs] played once more. */
    fun played(fromMs: Long, toMs: Long): ReplayHeatmap {
        val from = fromMs.coerceIn(0L, durationMs)
        val to = toMs.coerceIn(0L, durationMs)
        if (to <= from) return this
        val played = playedMs.copyOf()
        for (moment in momentAt(from)..momentAt(to - 1)) {
            played[moment] += (minOf(to, startOf(moment + 1)) - maxOf(from, startOf(moment))).toInt()
        }
        return ReplayHeatmap(durationMs, played)
    }

    /**
     * The graph's [PointCount] heights along the video, from 0 to 1 against the most watched - or null while no moment stands
     * out to show: until one has been watched at least [MinPeakTimes] times, and at least once more than the video's typical
     * moment. A video watched through once, or through twice alike, has no moment more replayed than another.
     *
     * Each point stands as high as the most watched moment it covers, so a scene replayed in a long film keeps its peak.
     */
    fun heights(): FloatArray? {
        val times = FloatArray(momentCount) { moment -> playedMs[moment].toFloat() / (startOf(moment + 1) - startOf(moment)).coerceAtLeast(1L) }
        val peak = times.max()
        val typical = times.sorted()[momentCount / 2]
        if (peak < MinPeakTimes || peak < typical + 1f) return null
        return FloatArray(PointCount) { point ->
            val first = point * momentCount / PointCount
            val last = ((point + 1) * momentCount / PointCount - 1).coerceAtLeast(first)
            (first..last).maxOf { times[it] } / peak
        }
    }

    fun toBytes(): ByteArray = ByteBuffer.allocate(momentCount * Int.SIZE_BYTES).apply { asIntBuffer().put(playedMs) }.array()

    private fun startOf(moment: Int): Long = durationMs * moment / momentCount

    private fun momentAt(positionMs: Long): Int = (positionMs * momentCount / durationMs).toInt().coerceIn(0, momentCount - 1)

    companion object {
        /** As many points as YouTube's graph has. */
        const val PointCount = 100

        /** About how long a moment is - shorter in a video too short for [PointCount] of them, longer past [MaxMomentCount]. */
        private const val MomentMs = 2_000L

        /** Moments enough for a film of over an hour at [MomentMs] each - 8 KB kept, at most, whatever its length. */
        private const val MaxMomentCount = 2_000

        /** How many times a moment has to have been watched for the graph to show. */
        private const val MinPeakTimes = 2f

        private fun momentCountFor(durationMs: Long): Int = (durationMs / MomentMs).toInt().coerceIn(PointCount, MaxMomentCount)

        /** Nothing watched yet of a video [durationMs] long - null for one whose length is unknown. */
        fun of(durationMs: Long): ReplayHeatmap? = durationMs.takeIf { it > 0L }?.let { ReplayHeatmap(it, IntArray(momentCountFor(it))) }

        /** What [toBytes] gave of a video [durationMs] long - null for anything else. */
        fun fromBytes(durationMs: Long, bytes: ByteArray): ReplayHeatmap? {
            if (durationMs <= 0L) return null
            val count = momentCountFor(durationMs)
            if (bytes.size != count * Int.SIZE_BYTES) return null
            return ReplayHeatmap(durationMs, IntArray(count).also { ByteBuffer.wrap(bytes).asIntBuffer().get(it) })
        }
    }
}

/** Where one part of the app - the library, or the Private Folder - keeps its videos' heatmaps, by each video's id in it. */
interface ReplayHeatmapStore {
    suspend fun read(videoId: Long): ReplayHeatmap?

    suspend fun write(videoId: Long, heatmap: ReplayHeatmap)
}
