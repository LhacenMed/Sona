package com.lhacenmed.sona.feature.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder

/**
 * The order a shuffled queue plays in - Auxio's `BetterShuffleOrder`.
 *
 * media3's default order scatters every added item at a random spot, which makes "play next" and
 * "add to queue" indistinguishable while shuffling. Here added items stay together, in the order they
 * were added, just before the item they were inserted in front of: inserted before the next item to
 * play, they play next; appended, they play last. A whole new queue starts on the item playback
 * starts from, with the rest shuffled after it, so shuffling from a chosen track plays that track first.
 */
@UnstableApi
internal class QueueShuffleOrder private constructor(private val shuffled: IntArray) : ShuffleOrder {

    /** Where each timeline index sits in [shuffled]. */
    private val positions = IntArray(shuffled.size).also { positions ->
        shuffled.forEachIndexed { position, index -> positions[index] = position }
    }

    constructor() : this(IntArray(0))

    override fun getLength(): Int = shuffled.size

    override fun getNextIndex(index: Int): Int = shuffled.getOrElse(positions[index] + 1) { C.INDEX_UNSET }

    override fun getPreviousIndex(index: Int): Int = shuffled.getOrElse(positions[index] - 1) { C.INDEX_UNSET }

    override fun getLastIndex(): Int = shuffled.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = shuffled.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder {
        val order = (0 until insertionCount).shuffled().toMutableList()
        if (startIndex in order) {
            order.remove(startIndex)
            order.add(0, startIndex)
        }
        return QueueShuffleOrder(order.toIntArray())
    }

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        if (shuffled.isEmpty()) return cloneAndSet(insertionCount, C.INDEX_UNSET)
        val pivot = if (insertionIndex < shuffled.size) positions[insertionIndex] else shuffled.size
        val shifted = shuffled.map { if (it >= insertionIndex) it + insertionCount else it }
        val inserted = (insertionIndex until insertionIndex + insertionCount).toList()
        return QueueShuffleOrder((shifted.subList(0, pivot) + inserted + shifted.subList(pivot, shifted.size)).toIntArray())
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removedCount = indexToExclusive - indexFrom
        return QueueShuffleOrder(
            shuffled
                .filterNot { it in indexFrom until indexToExclusive }
                .map { if (it >= indexToExclusive) it - removedCount else it }
                .toIntArray(),
        )
    }

    override fun cloneAndClear(): ShuffleOrder = QueueShuffleOrder()
}
