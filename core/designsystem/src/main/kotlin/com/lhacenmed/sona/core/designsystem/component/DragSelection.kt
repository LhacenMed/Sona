package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How far in from a list's top and bottom a drag scrolls it: Fossify's `dragselect_hotspot_height`. */
private val AutoScrollHotspotHeight = 56.dp

/**
 * The fastest a drag scrolls, in hotspot heights a second: Fossify's top speed - half a hotspot every
 * 25ms step - spread over every frame rather than taken in steps.
 */
private const val MAX_AUTO_SCROLL_HOTSPOTS_PER_SECOND = 20f

private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * The [DragSelection] of the list a row is drawn in, or null in a list without one.
 *
 * A row is drawn the same wherever it is listed, so it is told through the composition rather than
 * through a parameter every list would have to pass - as [LocalDragHandle] is.
 */
val LocalDragSelection = compositionLocalOf<DragSelection?> { null }

/**
 * Selecting a list's rows the way a file browser does - Fossify's `MyRecyclerView` drag selection and
 * `MyRecyclerViewAdapter` ranges, which it ports from afollestad's drag-select-recyclerview.
 *
 * Long pressing a row selects it and, without lifting the finger, dragging over other rows selects
 * every row from it to the finger; dragging back gives rows up again. Held near the list's top or
 * bottom, the drag scrolls the list, faster the closer it is to the edge. Long pressing a second row
 * also selects every row between the two. A tap forgets that first long press.
 *
 * The list tells it its selectable rows in order through [orderedKeys]; its rows tell it where they
 * are laid out, through [LocalDragSelection]. The selection itself stays in [selection], so a drag adds
 * to one gathered anywhere else.
 */
@Stable
class DragSelection internal constructor(
    private val selection: SelectionState,
    private val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    internal var orderedKeys: List<Any> = emptyList()
    internal var hotspotHeightPx = 0f

    /** How much of the list's bottom lies under the player, where the bottom hotspot stops short. */
    internal var bottomInsetPx = 0f

    private var list: LayoutCoordinates? = null
    private val rows = HashMap<Any, LayoutCoordinates>()

    /** The row long pressed last - Fossify's `lastLongPressedItem`. */
    private var lastLongPressedKey: Any? = null

    /** Whether a finger is on the list - a long press made without one, from an accessibility service, has no drag to start. */
    internal var isPointerDown = false

    internal var isDragging = false
        private set
    private var initialIndex = -1
    private var lastDraggedIndex = -1
    private var minReached = -1
    private var maxReached = -1

    private var pointerY = 0f
    /** How fast the list is scrolling under the finger, in pixels a second - negative towards its top. */
    private var autoScrollSpeed = 0f
    private var autoScrollJob: Job? = null

    /** A tap on a row - Fossify's `viewClicked`, which forgets the last long press. */
    fun onRowTap() {
        lastLongPressedKey = null
    }

    /** A long press on the row [key] - Fossify's `viewLongClicked` and `itemLongClicked`. */
    fun onRowLongPress(key: Any) {
        // A selection that has ended since forgets its long press, as Fossify's action mode does.
        if (!selection.isActive) lastLongPressedKey = null
        val keys = orderedKeys
        val index = keys.indexOf(key)
        if (index == -1) return
        selection.selectAll(listOf(key))
        if (isPointerDown) startDragging(index)
        val lastIndex = lastLongPressedKey?.let(keys::indexOf) ?: -1
        if (lastIndex != -1) {
            selection.selectAll(keys.subList(minOf(lastIndex, index), maxOf(lastIndex, index) + 1))
        }
        lastLongPressedKey = key
    }

    internal fun onListPlaced(coordinates: LayoutCoordinates) {
        list = coordinates
    }

    internal fun onRowPlaced(key: Any, coordinates: LayoutCoordinates) {
        rows[key] = coordinates
    }

    internal fun onRowRemoved(key: Any, coordinates: LayoutCoordinates) {
        if (rows[key] === coordinates) rows.remove(key)
    }

    /** The finger dragging at [y] down the list - Fossify's `ACTION_MOVE` while drag selection is on. */
    internal fun onDrag(y: Float) {
        pointerY = y
        updateAutoScroll(y)
        selectToRowAt(y)
    }

    internal fun stopDragging() {
        isDragging = false
        autoScrollSpeed = 0f
        autoScrollJob?.cancel()
    }

    private fun startDragging(index: Int) {
        initialIndex = index
        lastDraggedIndex = -1
        minReached = -1
        maxReached = -1
        isDragging = true
    }

    private fun selectToRowAt(y: Float) {
        val key = rowKeyAt(y) ?: return
        val index = orderedKeys.indexOf(key)
        if (index == -1 || index == lastDraggedIndex) return
        lastDraggedIndex = index
        if (minReached == -1) minReached = index
        if (maxReached == -1) maxReached = index
        maxReached = maxOf(maxReached, index)
        minReached = minOf(minReached, index)
        selectRange(initialIndex, index, minReached, maxReached)
        if (minReached != maxReached) lastLongPressedKey = null
        if (initialIndex == index) {
            minReached = index
            maxReached = index
        }
    }

    /**
     * Fossify's `selectItemRange`: every row from [from] to [to] selected, and every row the drag
     * reached between [min] and [max] beyond that range given up again - the long pressed row aside.
     */
    private fun selectRange(from: Int, to: Int, min: Int, max: Int) {
        val keys = orderedKeys
        val selected = ArrayList<Any>()
        val deselected = ArrayList<Any>()
        fun MutableList<Any>.addAt(indices: Iterable<Int>) = indices.forEach { index -> keys.getOrNull(index)?.let(::add) }
        when {
            from == to -> deselected.addAt((min..max).filter { it != from })
            to < from -> {
                selected.addAt(to..from)
                if (min > -1 && min < to) deselected.addAt((min until to).filter { it != from })
                if (max > -1) deselected.addAt(from + 1..max)
            }
            else -> {
                selected.addAt(from..to)
                if (max > -1 && max > to) deselected.addAt((to + 1..max).filter { it != from })
                if (min > -1) deselected.addAt(min until from)
            }
        }
        selection.deselectAll(deselected)
        selection.selectAll(selected)
    }

    private fun rowKeyAt(y: Float): Any? {
        val list = list?.takeIf { it.isAttached } ?: return null
        return rows.entries.firstOrNull { (_, row) ->
            row.isAttached && list.localBoundingBoxOf(row, clipBounds = false).let { y >= it.top && y < it.bottom }
        }?.key
    }

    /**
     * Scrolls the list while the finger at [y] is in a hotspot, every frame by how far that frame's time
     * carries it at [autoScrollSpeedAt] - so the list glides at an even pace on any display, instead of
     * jumping in timed steps. Rows passing under a finger held still join the drag as they arrive.
     */
    private fun updateAutoScroll(y: Float) {
        autoScrollSpeed = autoScrollSpeedAt(y)
        if (autoScrollSpeed == 0f || autoScrollJob?.isActive == true) return
        autoScrollJob = scope.launch {
            var lastFrameNanos = withFrameNanos { it }
            while (autoScrollSpeed != 0f) {
                val frameNanos = withFrameNanos { it }
                listState.scrollBy(autoScrollSpeed * (frameNanos - lastFrameNanos) / NANOS_PER_SECOND)
                lastFrameNanos = frameNanos
                selectToRowAt(pointerY)
            }
        }
    }

    /**
     * How fast the list scrolls under the finger at [y], in pixels a second: in proportion to how deep
     * into a hotspot it is, as Fossify's is, up to full speed at the hotspot's far edge - and held at full
     * speed past it, over the player or above the list, as `ItemTouchHelper` caps its drag scroll. A
     * finger held anywhere beyond the rows keeps the list moving rather than stopping it.
     */
    private fun autoScrollSpeedAt(y: Float): Float {
        val listHeight = list?.size?.height?.toFloat() ?: return 0f
        val bottomHotspotTop = listHeight - bottomInsetPx - hotspotHeightPx
        val depth = when {
            y > bottomHotspotTop -> y - bottomHotspotTop
            y < hotspotHeightPx -> y - hotspotHeightPx
            else -> return 0f
        }
        return (depth / hotspotHeightPx).coerceIn(-1f, 1f) * hotspotHeightPx * MAX_AUTO_SCROLL_HOTSPOTS_PER_SECOND
    }
}

/**
 * A [DragSelection] over [listState]'s rows, whose selectable keys in list order are [orderedKeys] -
 * built once per change of rows, not on every move of a drag.
 */
@Composable
fun rememberDragSelection(
    selection: SelectionState,
    listState: LazyListState,
    orderedKeys: List<Any>,
): DragSelection {
    val scope = rememberCoroutineScope()
    val dragSelection = remember(selection, listState, scope) { DragSelection(selection, listState, scope) }
    val hotspotHeightPx = with(LocalDensity.current) { AutoScrollHotspotHeight.toPx() }
    val bottomInsetPx = with(LocalDensity.current) { LocalBottomContentPadding.current.toPx() }
    SideEffect {
        dragSelection.orderedKeys = orderedKeys
        dragSelection.hotspotHeightPx = hotspotHeightPx
        dragSelection.bottomInsetPx = bottomInsetPx
    }
    return dragSelection
}

/**
 * The list [dragSelection] selects over. Once a row's long press starts a drag, the finger's moves are
 * taken before the list sees them, so they select rather than scroll - Fossify's `dispatchTouchEvent`.
 */
fun Modifier.dragSelection(dragSelection: DragSelection): Modifier = this
    .onPlaced(dragSelection::onListPlaced)
    .pointerInput(dragSelection) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            dragSelection.isPointerDown = true
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.first()
                    if (dragSelection.isDragging) {
                        event.changes.forEach { it.consume() }
                        if (change.pressed) dragSelection.onDrag(change.position.y)
                    }
                } while (event.changes.any { it.pressed })
            } finally {
                dragSelection.isPointerDown = false
                dragSelection.stopDragging()
            }
        }
    }

/** A row of [dragSelection]'s list, found under the finger by where it is laid out. */
internal fun Modifier.dragSelectableRow(dragSelection: DragSelection, key: Any): Modifier =
    this then DragSelectableRowElement(dragSelection, key)

private data class DragSelectableRowElement(
    val dragSelection: DragSelection,
    val key: Any,
) : ModifierNodeElement<DragSelectableRowNode>() {
    override fun create() = DragSelectableRowNode(dragSelection, key)

    override fun update(node: DragSelectableRowNode) = node.update(dragSelection, key)
}

private class DragSelectableRowNode(
    private var dragSelection: DragSelection,
    private var key: Any,
) : Modifier.Node(), LayoutAwareModifierNode {

    private var coordinates: LayoutCoordinates? = null

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        dragSelection.onRowPlaced(key, coordinates)
    }

    override fun onDetach() {
        coordinates?.let { dragSelection.onRowRemoved(key, it) }
        coordinates = null
    }

    fun update(dragSelection: DragSelection, key: Any) {
        coordinates?.let { this.dragSelection.onRowRemoved(this.key, it) }
        this.dragSelection = dragSelection
        this.key = key
        coordinates?.let { dragSelection.onRowPlaced(key, it) }
    }
}
