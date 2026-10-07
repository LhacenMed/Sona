package com.lhacenmed.sona.core.designsystem.component.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo

/** How far a list has to be from its top for a way back to be offered: a quarter of a screen. */
private const val AwayFromTopFraction = 0.25f

/** The row a list marks as playing: where it sits among the list's items, and whether playback is going. */
@Immutable
data class PlayingRow(
    val index: Int,
    val isPlaying: Boolean,
)

/**
 * A screen's scrolling list as what stands around the screen follows it - its top app bar, its
 * [FloatingActionButtonStack][com.lhacenmed.sona.core.designsystem.component.fab.FloatingActionButtonStack] -
 * told by how the list itself has scrolled - whether it has left its top, how far, how far it can still go -
 * never by where it sits on screen, so nothing laid over or around it, the player included, can change the
 * answers.
 */
internal abstract class ScreenList {
    /** What the list scrolls by - a list followed by another state is another list. */
    abstract val source: Any

    /** How much of the list the window shows: the screen's list is the one it shows most of. */
    var visibleArea by mutableFloatStateOf(0f)
        private set

    /** Where the list's top stands in the window, and the stretch of the window it shows in. */
    private var windowTopPx by mutableFloatStateOf(0f)
    private var shownTopPx by mutableFloatStateOf(0f)
    private var shownBottomPx by mutableFloatStateOf(0f)

    var isFastScrolling: () -> Boolean = { false }
    lateinit var scrollToTop: suspend () -> Unit

    /** The row the list marks as playing, if it lays one out - state, so whatever follows it is told when it changes. */
    var playingRow: () -> PlayingRow? by mutableStateOf({ null })

    /** What the way to the playing row does: brings the row at the index it is given into view. */
    lateinit var scrollToRow: suspend (index: Int) -> Unit

    /** How far the list has scrolled from its top, in pixels. */
    protected abstract val scrolledPx: Float

    /** How far the list can still scroll towards its end, in pixels - infinite while its end is not laid out. */
    protected abstract val remainingPx: Float

    /** How tall the part of the list on screen is, in pixels. */
    protected abstract val viewportPx: Float

    /** Whether the list scrolls at all - one that fits has no end to come near and no top to go back to. */
    protected abstract val canScroll: Boolean

    /** Whether the list has scrolled from its top at all - what a top app bar lifts by. */
    abstract val isScrolled: Boolean

    /** Whether the list is far enough from its top for a way back to be worth offering. */
    val isAwayFromTop: Boolean
        get() = canScroll && scrolledPx > viewportPx * AwayFromTopFraction

    /** Whether the list has come within [distancePx] of its end - where a stack that tall would cover its last rows. */
    fun isNearEnd(distancePx: Float): Boolean = canScroll && remainingPx < distancePx

    /** Where the row at [index] lies from the list's top, in pixels - null while it is not laid out. */
    protected open fun rowSpanPx(index: Int): ClosedFloatingPointRange<Float>? = null

    /**
     * Whether the row at [index] shows whole on screen: laid out within the part of the window the list
     * shows in, and above [uncoveredBottomPx] - the window's bottom, less whatever stands over it.
     */
    fun showsRow(index: Int, uncoveredBottomPx: Float): Boolean {
        val span = rowSpanPx(index) ?: return false
        return windowTopPx + span.start >= shownTopPx &&
            windowTopPx + span.endInclusive <= minOf(shownBottomPx, uncoveredBottomPx)
    }

    fun onPositioned(coordinates: LayoutCoordinates) {
        val bounds = coordinates.boundsInWindow()
        visibleArea = bounds.width * bounds.height
        windowTopPx = coordinates.positionInWindow().y
        shownTopPx = bounds.top
        shownBottomPx = bounds.bottom
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

    override val isScrolled: Boolean
        get() = state.canScrollBackward

    override fun rowSpanPx(index: Int): ClosedFloatingPointRange<Float>? =
        state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            ?.let { row -> row.offset.toFloat()..(row.offset + row.size).toFloat() }
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

    override val isScrolled: Boolean
        get() = state.value > 0
}

/**
 * The scrolling lists a screen shows, each marked with [screenList] - one screen's worth, provided around
 * the screen and everything that stands around it by
 * [FloatingActionButtonStack][com.lhacenmed.sona.core.designsystem.component.fab.FloatingActionButtonStack].
 */
@Stable
internal class ScreenLists {
    val lists = mutableStateListOf<ScreenList>()

    /** The screen's list: the one the window shows most of - a pager's, the page on screen. */
    val current: ScreenList? by derivedStateOf {
        lists.filter { it.visibleArea > 0f }.maxByOrNull { it.visibleArea }
    }
}

internal val LocalScreenLists = staticCompositionLocalOf<ScreenLists?> { null }

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
 * Brings the row at [index] to the list's top in one short glide, however far it is - first jumping to a
 * screen short of it, as [scrollBackToTop] does on its way back.
 */
suspend fun LazyListState.scrollToRow(index: Int) {
    val rowsOnScreen = layoutInfo.visibleItemsInfo.size
    when {
        index > firstVisibleItemIndex + rowsOnScreen -> scrollToItem(index - rowsOnScreen)
        index < firstVisibleItemIndex - rowsOnScreen -> scrollToItem(index + rowsOnScreen)
    }
    animateScrollToItem(index)
}

/**
 * Makes this the screen's list whenever the window shows more of it than of any other - a list in a pager
 * while its page is the one on screen: the one the screen's top app bar lifts over once it has scrolled,
 * and its FABs follow, stepping aside as it nears its end and offering a way back to its top - or, near
 * its top, to its [playingRow] while that is off screen.
 *
 * Applied to the layout the list fills. [FastScroller][com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller]
 * applies it itself, so every lazy list with a fast scroller has it. [isFastScrolling] steps the stack
 * aside while it holds; [scrollToTop] is what the way back does, and [scrollToRow] the way to the row.
 */
fun Modifier.screenList(
    state: LazyListState,
    isFastScrolling: () -> Boolean = { false },
    scrollToTop: suspend () -> Unit = { state.scrollBackToTop() },
    playingRow: () -> PlayingRow? = { null },
    scrollToRow: suspend (index: Int) -> Unit = { state.scrollToRow(it) },
): Modifier = this then ScreenListElement(state, isFastScrolling, scrollToTop, playingRow, scrollToRow)

/** [screenList] for a column scrolled with `verticalScroll(state)`, applied before it. */
fun Modifier.screenList(state: ScrollState): Modifier =
    this then ScreenListElement(
        state = state,
        isFastScrolling = { false },
        scrollToTop = { state.animateScrollTo(0) },
        playingRow = { null },
        scrollToRow = {},
    )

private class ScreenListElement(
    private val state: Any,
    private val isFastScrolling: () -> Boolean,
    private val scrollToTop: suspend () -> Unit,
    private val playingRow: () -> PlayingRow?,
    private val scrollToRow: suspend (index: Int) -> Unit,
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
        playingRow = this@ScreenListElement.playingRow
        scrollToRow = this@ScreenListElement.scrollToRow
    }

    override fun equals(other: Any?): Boolean =
        other is ScreenListElement && other.state === state &&
            other.isFastScrolling === isFastScrolling && other.scrollToTop === scrollToTop &&
            other.playingRow === playingRow && other.scrollToRow === scrollToRow

    override fun hashCode(): Int = System.identityHashCode(state)

    override fun InspectorInfo.inspectableProperties() {
        name = "screenList"
    }
}

private class ScreenListNode(var list: ScreenList) :
    Modifier.Node(), CompositionLocalConsumerModifierNode, GlobalPositionAwareModifierNode {

    private var screenLists: ScreenLists? = null

    override fun onAttach() {
        screenLists = currentValueOf(LocalScreenLists)
        screenLists?.lists?.add(list)
    }

    override fun onDetach() {
        screenLists?.lists?.remove(list)
        screenLists = null
    }

    fun replaceList(newList: ScreenList) {
        screenLists?.lists?.remove(list)
        list = newList
        screenLists?.lists?.add(newList)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        list.onPositioned(coordinates)
    }
}
