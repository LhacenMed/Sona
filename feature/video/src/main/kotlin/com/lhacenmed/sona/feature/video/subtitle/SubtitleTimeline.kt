package com.lhacenmed.sona.feature.video.subtitle

/** One stretch of a subtitle file. A null [endUs] lasts until the next stretch begins, or for good after the last. */
internal class TimedCues<T>(val startUs: Long, val endUs: Long?, val cues: List<T>)

/**
 * A subtitle file's lines against its own clock - the player asked for no part of it, so they are read at any
 * position, moved by any offset, at once and in either direction.
 */
internal class SubtitleTimeline<T>(stretches: List<TimedCues<T>>) {

    private val sorted = stretches.sortedBy { it.startUs }

    private val endsUs = sorted.mapIndexed { index, stretch ->
        stretch.endUs ?: sorted.getOrNull(index + 1)?.startUs ?: Long.MAX_VALUE
    }

    /** Every line showing at [positionUs], those that began first first. */
    fun cuesAt(positionUs: Long): List<T> {
        var shown: MutableList<T>? = null
        for (index in sorted.indices) {
            val stretch = sorted[index]
            if (stretch.startUs > positionUs) break
            if (positionUs < endsUs[index]) (shown ?: ArrayList<T>().also { shown = it }).addAll(stretch.cues)
        }
        return shown.orEmpty()
    }
}
