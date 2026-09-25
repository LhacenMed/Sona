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
 *
 * An empty order can be armed with [restoring] a saved one: the next queue set on the player takes that
 * order instead of a new one, provided it is the same length - which is how a queue comes back after the
 * app was closed still playing in the order it was.
 *
 * An order can likewise be armed with [insertingAt] a place in it: the next item added to the queue plays
 * there, wherever its index puts it - which is how a track taken out of a shuffled queue goes back to
 * exactly where it played.
 */
@UnstableApi
internal class QueueShuffleOrder private constructor(
    private val shuffled: IntArray,
    private val restoredOrder: IntArray? = null,
    private val insertionPosition: Int? = null,
) : ShuffleOrder {

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

    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder =
        restoredOrder?.takeIf { it.size == insertionCount }?.let(::QueueShuffleOrder)
            ?: startingFrom(insertionCount, startIndex)

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        if (shuffled.isEmpty()) return startingFrom(insertionCount, C.INDEX_UNSET)
        val pivot = insertionPosition?.coerceIn(0, shuffled.size)
            ?: if (insertionIndex < shuffled.size) positions[insertionIndex] else shuffled.size
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

    companion object {
        /**
         * A new order over [count] items, [startIndex] first and the rest in random order - the one
         * place a shuffle is dealt, whether a queue is set shuffled or shuffle is turned on mid-queue.
         */
        fun startingFrom(count: Int, startIndex: Int): QueueShuffleOrder {
            val order = (0 until count).shuffled().toMutableList()
            if (startIndex in order) {
                order.remove(startIndex)
                order.add(0, startIndex)
            }
            return QueueShuffleOrder(order.toIntArray())
        }

        /** An order that plays the queue's items as [order] lists them - how a shuffled queue is rearranged. */
        fun of(order: IntArray): QueueShuffleOrder = QueueShuffleOrder(order)

        /** [order] as it plays, with the next item added to the queue playing at [playPosition] in it. */
        fun insertingAt(order: IntArray, playPosition: Int): QueueShuffleOrder =
            QueueShuffleOrder(order, insertionPosition = playPosition)

        /** An empty order that hands [order] to the next queue of the same length set on the player. */
        fun restoring(order: IntArray): QueueShuffleOrder = QueueShuffleOrder(IntArray(0), restoredOrder = order)
    }
}
