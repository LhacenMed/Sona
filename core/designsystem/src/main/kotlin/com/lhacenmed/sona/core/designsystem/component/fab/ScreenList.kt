package com.lhacenmed.sona.core.designsystem.component.fab

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo

/** How far a list has to be from its top for a way back to be offered: a quarter of a screen. */
private const val AwayFromTopFraction = 0.25f

/**
 * A screen's scrolling list as its [FloatingActionButtonStack] follows it, told by how the list itself
 * has scrolled - how far from its top, how far it can still go - never by where it sits on screen, so
 * nothing laid over or around it, the player included, can change the answers.
 */
internal abstract class ScreenList {
    /** What the list scrolls by - a list followed by another state is another list. */
    abstract val source: Any

    /** How much of the list the window shows: the screen's list is the one it shows most of. */
    var visibleArea by mutableFloatStateOf(0f)
        private set

    var isFastScrolling: () -> Boolean = { false }
    lateinit var scrollToTop: suspend () -> Unit

    /** How far the list has scrolled from its top, in pixels. */
    protected abstract val scrolledPx: Float

    /** How far the list can still scroll towards its end, in pixels - infinite while its end is not laid out. */
    protected abstract val remainingPx: Float

    /** How tall the part of the list on screen is, in pixels. */
    protected abstract val viewportPx: Float

    /** Whether the list scrolls at all - one that fits has no end to come near and no top to go back to. */
    protected abstract val canScroll: Boolean

    /** Whether the list is far enough from its top for a way back to be worth offering. */
    val isAwayFromTop: Boolean
        get() = canScroll && scrolledPx > viewportPx * AwayFromTopFraction

    /** Whether the list has come within [distancePx] of its end - where a stack that tall would cover its last rows. */
    fun isNearEnd(distancePx: Float): Boolean = canScroll && remainingPx < distancePx

    fun onPositioned(coordinates: LayoutCoordinates) {
        val bounds = coordinates.boundsInWindow()
        visibleArea = bounds.width * bounds.height
    }
}

private class LazyScreenList(val state: LazyListState) : ScreenList() {
    override val source: Any get() = state

    // The rows scrolled past are no longer laid out, so they are taken at the height of those on screen -
    // exact for a list whose rows share one shape. A distance rather than a count of rows: the rows on
    // screen number one more or one fewer as one slides in or out, which would flicker the answer.
    override val scrolledPx: Float
        get() {
            val rowsOnScreen = state.layoutInfo.visibleItemsInfo
            if (rowsOnScreen.isEmpty()) return 0f
            val rowHeight = rowsOnScreen.sumOf { it.size } / rowsOnScreen.size.toFloat()
            return state.firstVisibleItemIndex * rowHeight + state.firstVisibleItemScrollOffset
        }

    override val remainingPx: Float
        get() {
            val layoutInfo = state.layoutInfo
            val lastRow = layoutInfo.visibleItemsInfo.lastOrNull() ?: return Float.POSITIVE_INFINITY
            if (lastRow.index != layoutInfo.totalItemsCount - 1) return Float.POSITIVE_INFINITY
            val contentEnd = lastRow.offset + lastRow.size + layoutInfo.afterContentPadding
            return (contentEnd - layoutInfo.viewportEndOffset).coerceAtLeast(0).toFloat()
        }

    override val viewportPx: Float
        get() = state.layoutInfo.viewportSize.height.toFloat()

    override val canScroll: Boolean
        get() = state.canScrollForward || state.canScrollBackward
}

private class ScrollScreenList(val state: ScrollState) : ScreenList() {
    override val source: Any get() = state

    override val scrolledPx: Float
        get() = state.value.toFloat()

    override val remainingPx: Float
        get() = (state.maxValue - state.value).toFloat()

    override val viewportPx: Float
        get() = state.viewportSize.toFloat()

    // Before its first layout a column's end is not known yet, which reads as scrolling without end.
    override val canScroll: Boolean
        get() = state.maxValue in 1 until Int.MAX_VALUE
}

/**
 * Takes the list back to its first row in one short glide, however far down it is. A list more than a
 * screen down first jumps to a screen from the top: scrolling the whole way would compose every row in
 * between, stuttering exactly where it should be fastest.
 */
suspend fun LazyListState.scrollBackToTop() {
    val rowsOnScreen = layoutInfo.visibleItemsInfo.size
    if (firstVisibleItemIndex > rowsOnScreen) scrollToItem(rowsOnScreen)
    animateScrollToItem(0)
}

/**
 * Makes this the list its screen's [FloatingActionButtonStack] follows - stepping the stack aside as the
 * list nears its end and offering a way back to its top - whenever the window shows more of it than of
 * any other. A list in a pager is followed while its page is the one on screen.
 *
 * Applied to the layout the list fills. [FastScroller][com.lhacenmed.sona.core.designsystem.component.FastScroller]
 * applies it itself, so every lazy list with a fast scroller has it. [isFastScrolling] steps the stack
 * aside while it holds; [scrollToTop] is what the way back does.
 */
fun Modifier.screenList(
    state: LazyListState,
    isFastScrolling: () -> Boolean = { false },
    scrollToTop: suspend () -> Unit = { state.scrollBackToTop() },
): Modifier = this then ScreenListElement(state, isFastScrolling, scrollToTop)

/** [screenList] for a column scrolled with `verticalScroll(state)`, applied before it. */
fun Modifier.screenList(state: ScrollState): Modifier =
    this then ScreenListElement(state, isFastScrolling = { false }, scrollToTop = { state.animateScrollTo(0) })

private class ScreenListElement(
    private val state: Any,
    private val isFastScrolling: () -> Boolean,
    private val scrollToTop: suspend () -> Unit,
) : ModifierNodeElement<ScreenListNode>() {

    override fun create() = ScreenListNode(newList())

    override fun update(node: ScreenListNode) {
        if (node.list.source !== state) node.replaceList(newList()) else node.list.configure()
    }

    private fun newList(): ScreenList = when (state) {
        is LazyListState -> LazyScreenList(state)
        is ScrollState -> ScrollScreenList(state)
        else -> error("Not a list state: $state")
    }.configure()

    private fun ScreenList.configure(): ScreenList = apply {
        isFastScrolling = this@ScreenListElement.isFastScrolling
        scrollToTop = this@ScreenListElement.scrollToTop
    }

    override fun equals(other: Any?): Boolean =
        other is ScreenListElement && other.state === state &&
            other.isFastScrolling === isFastScrolling && other.scrollToTop === scrollToTop

    override fun hashCode(): Int = System.identityHashCode(state)

    override fun InspectorInfo.inspectableProperties() {
        name = "screenList"
    }
}

private class ScreenListNode(var list: ScreenList) :
    Modifier.Node(), CompositionLocalConsumerModifierNode, GlobalPositionAwareModifierNode {

    private var stack: FloatingActionButtonStackState? = null

    override fun onAttach() {
        stack = currentValueOf(LocalFloatingActionButtonStack)
        stack?.lists?.add(list)
    }

    override fun onDetach() {
        stack?.lists?.remove(list)
        stack = null
    }

    fun replaceList(newList: ScreenList) {
        stack?.lists?.remove(list)
        list = newList
        stack?.lists?.add(newList)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        list.onPositioned(coordinates)
    }
}
