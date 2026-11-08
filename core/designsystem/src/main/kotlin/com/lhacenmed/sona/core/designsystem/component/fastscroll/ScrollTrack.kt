package com.lhacenmed.sona.core.designsystem.component.fastscroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.roundToInt

/**
 * What a [FastScroller] moves and follows: a lazy list, or a column scrolled as a whole - one thumb, one popup and
 * one set of touches for both, told apart only here.
 */
internal sealed interface ScrollTrack {
    /** What the list scrolls by - what its screen marks it with. */
    val source: Any

    /** Whether there is anywhere to scroll - a list that fits has no thumb. */
    val canScroll: Boolean

    val isScrollInProgress: Boolean

    /** Where the list stands, read only to tell that it has moved. */
    val position: Any

    /** The first row on screen - what the popup names the section of. A column has no rows of its own. */
    val firstVisibleIndex: Int

    /** How far through its content the list is scrolled, 0 at its top and 1 at its end. */
    fun scrollFraction(): Float

    /** Puts the list [fraction] of the way through its content. */
    fun scrollToFraction(fraction: Float)

    suspend fun stopScroll()
}

internal class LazyListTrack(private val state: LazyListState) : ScrollTrack {
    private val metrics = ListScrollMetrics()

    override val source: Any get() = state
    override val canScroll: Boolean get() = state.canScrollForward || state.canScrollBackward
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress
    override val position: Any get() = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
    override val firstVisibleIndex: Int get() = state.firstVisibleItemIndex
    override fun scrollFraction(): Float = state.scrollFraction(metrics)
    override fun scrollToFraction(fraction: Float) = state.scrollToFraction(fraction, metrics)
    override suspend fun stopScroll() = state.stopScroll()
}

internal class ColumnTrack(private val state: ScrollState) : ScrollTrack {
    override val source: Any get() = state

    // Before its first layout a column's end is not known yet, which reads as scrolling without end.
    override val canScroll: Boolean get() = state.maxValue in 1 until Int.MAX_VALUE
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress
    override val position: Any get() = state.value
    override val firstVisibleIndex: Int get() = 0
    override fun scrollFraction(): Float = if (canScroll) state.value.toFloat() / state.maxValue else 0f

    // Every row of a column is composed already, so going straight to the place costs nothing more than a frame.
    override fun scrollToFraction(fraction: Float) {
        if (!canScroll) return
        state.dispatchRawDelta((fraction.coerceIn(0f, 1f) * state.maxValue).roundToInt() - state.value.toFloat())
    }

    override suspend fun stopScroll() = state.stopScroll()
}
