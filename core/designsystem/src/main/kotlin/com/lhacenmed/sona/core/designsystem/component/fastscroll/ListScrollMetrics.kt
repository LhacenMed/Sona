package com.lhacenmed.sona.core.designsystem.component.fastscroll

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.roundToInt

/**
 * How long a lazy list is and where along it the list stands, in px - what the fast scroller places its
 * thumb by, and turns the thumb's place back into a row by.
 *
 * A lazy list knows only the rows it has laid out, so the rest have to be reckoned. Taking every row to be
 * as tall as the average of those on screen - `RecyclerView`'s own estimate - is exact for a list of one
 * kind of row, and nothing else: on a detail screen, headings, a divider, artist rows and then tracks, the
 * average changed with every kind of row scrolling in or out, and the whole list's length with it. The
 * thumb jumped under an even scroll, and a dragged thumb put the list where its own next frame placed the
 * thumb elsewhere, so it shook under the finger.
 *
 * Here a row once laid out counts at its own size, and a row not laid out yet at the size most rows have -
 * the list's bulk, which its few odd rows never move. So the reckoning is the same whichever rows are on
 * screen, and placing the thumb and dragging it are exact inverses. What is remembered is let go as soon as
 * a row is not the one measured at its place - a search, a sort, a section arriving - or the list changes
 * length.
 *
 * Plain fields rather than snapshot state: it is filled from the layout every read already observes, and
 * filling it must never itself ask for another layout.
 */
internal class ListScrollMetrics {
    /** Each row's key and size where it was laid out; [UNKNOWN] where it has not been. */
    private var keys = arrayOfNulls<Any>(0)
    private var sizes = IntArray(0)

    /** How many rows were laid out at each size - the most shared of which stands in for the rest. */
    private val sizeCounts = HashMap<Int, Int>()
    private var knownSizeSum = 0L
    private var knownCount = 0
    private var typicalSize = 0

    /** Takes in the rows [info] has laid out - cheap, as only the ones on screen are looked at. */
    fun update(info: LazyListLayoutInfo) {
        val count = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (count != sizes.size || visible.any { it.index < count && keys[it.index].let { key -> key != null && key != it.key } }) {
            reset(count)
        }
        var changed = false
        for (item in visible) {
            val index = item.index
            if (index >= count) continue
            // The gap after a row is part of what it takes up along the list.
            val size = item.size + info.mainAxisItemSpacing
            val old = sizes[index]
            if (old == size) continue
            if (old != UNKNOWN) forget(old) else knownCount++
            sizes[index] = size
            keys[index] = item.key
            sizeCounts[size] = (sizeCounts[size] ?: 0) + 1
            knownSizeSum += size
            changed = true
        }
        // The most shared size, the larger on a tie, so the choice never depends on the order counted in.
        if (changed) typicalSize = sizeCounts.maxWithOrNull(compareBy({ it.value }, { it.key }))?.key ?: 0
    }

    /** Whether anything has been laid out to reckon by. */
    val isKnown: Boolean get() = typicalSize > 0

    /** Every row together. */
    val contentSizePx: Float get() = (knownSizeSum + (sizes.size - knownCount).toLong() * typicalSize).toFloat()

    /** The rows before [index] together. */
    fun offsetOf(index: Int): Float {
        var offset = 0L
        for (i in 0 until index.coerceAtMost(sizes.size)) offset += sizeAt(i)
        return offset.toFloat()
    }

    /** The row [offsetPx] down the list falls in, and how far into it. */
    fun positionAt(offsetPx: Float): Pair<Int, Int> {
        var remaining = offsetPx.roundToInt().coerceAtLeast(0)
        for (i in sizes.indices) {
            val size = sizeAt(i)
            if (remaining < size || i == sizes.lastIndex) return i to remaining.coerceAtMost(size)
            remaining -= size
        }
        return 0 to 0
    }

    private fun sizeAt(index: Int): Int = sizes[index].let { if (it == UNKNOWN) typicalSize else it }

    private fun forget(size: Int) {
        val left = (sizeCounts[size] ?: 1) - 1
        if (left == 0) sizeCounts.remove(size) else sizeCounts[size] = left
        knownSizeSum -= size
    }

    private fun reset(count: Int) {
        keys = arrayOfNulls(count)
        sizes = IntArray(count) { UNKNOWN }
        sizeCounts.clear()
        knownSizeSum = 0
        knownCount = 0
        typicalSize = 0
    }

    private companion object {
        const val UNKNOWN = -1
    }
}

/**
 * How far through its content the list is scrolled, 0 at its top and 1 at its end, by [metrics]. Pinned to
 * its ends wherever the list cannot scroll further, so the thumb reaches them exactly.
 */
internal fun LazyListState.scrollFraction(metrics: ListScrollMetrics): Float {
    val info = layoutInfo
    metrics.update(info)
    if (!canScrollBackward) return 0f
    if (!canScrollForward) return 1f
    val range = scrollRangePx(info, metrics)
    val offset = metrics.offsetOf(firstVisibleItemIndex) + firstVisibleItemScrollOffset
    return if (range > 0f) (offset / range).coerceIn(0f, 1f) else 0f
}

/**
 * Puts the list where a thumb at [fraction] of its travel stands for - Auxio's `scrollToThumbOffset`, by
 * row rather than by pixels, and by the very reckoning [scrollFraction] places the thumb by.
 *
 * Auxio scrolls by the pixels between where the thumb was and where it is, which a `RecyclerView` can do
 * cheaply. A lazy list cannot: to scroll some distance it lays out every row it passes, one at a time, to
 * learn how tall each is - so a thumb dragged down a long list had it compose thousands of rows off screen
 * in a single frame. Here the list is simply put at the row and offset the thumb stands for: only the rows
 * that end up on screen are laid out, however long the list, and a request made several times between
 * frames is laid out once.
 */
internal fun LazyListState.scrollToFraction(fraction: Float, metrics: ListScrollMetrics) {
    val info = layoutInfo
    metrics.update(info)
    if (!metrics.isKnown) return
    val target = fraction.coerceIn(0f, 1f) * scrollRangePx(info, metrics).coerceAtLeast(0f)
    val (index, offset) = metrics.positionAt(target)
    requestScrollToItem(index, offset)
}

private fun scrollRangePx(info: LazyListLayoutInfo, metrics: ListScrollMetrics): Float =
    metrics.contentSizePx + info.beforeContentPadding + info.afterContentPadding - info.viewportSize.height
