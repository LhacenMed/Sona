package com.lhacenmed.sona.core.designsystem.component.header

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far a header standing over a list has collapsed - Auxio's `AppBarLayout` offset, scrolling
 * `exitUntilCollapsed|snap`: a detail screen's header, the library's shortcuts.
 *
 * The header and its list move as one: scrolling up collapses the header first and scrolls the list after;
 * scrolling down scrolls the list back to its top first and opens the header with what is left - see
 * [nestedScrollConnection] - and a drag on the header itself does the same from the other side - see
 * [collapsingHeaderDrag]. So it collapses the same over one row as over a thousand, and nothing about the
 * list can leave it stuck part-way: let go part-way, and it springs open or collapsed, whichever is nearer.
 * Every value here is read while laying out or drawing, never in composition, so a collapse recomposes
 * nothing.
 */
@Stable
class CollapsingHeaderState internal constructor(private val scope: CoroutineScope) {

    /** How tall the header is open. */
    internal var heightPx by mutableIntStateOf(0)

    /** How much of it never collapses - a bar it collapses into. */
    internal var pinnedPx by mutableIntStateOf(0)

    /** How far scrolling has collapsed it. Kept within [rangePx] when read. */
    private var collapsedPx by mutableFloatStateOf(0f)

    private var settleJob: Job? = null

    /** How far it collapses at most. */
    internal val rangePx: Float
        get() = (heightPx - pinnedPx).coerceAtLeast(0).toFloat()

    /** How far it has collapsed, in pixels: 0 open, [rangePx] collapsed. */
    internal val offsetPx: Float
        get() = collapsedPx.coerceIn(0f, rangePx)

    /** How far it has collapsed, 0 open and 1 collapsed. */
    val fraction: Float
        get() = if (rangePx == 0f) 0f else offsetPx / rangePx

    /**
     * Collapses it by [delta] of a scroll - negative up, positive down - as far as it goes, and returns how
     * much of [delta] that took. Any settle in flight gives way: the finger has it.
     */
    internal fun collapseBy(delta: Float): Float {
        settleJob?.cancel()
        val before = offsetPx
        collapsedPx = (before - delta).coerceIn(0f, rangePx)
        return before - collapsedPx
    }

    /**
     * Settles it, let go part-way, open or collapsed, whichever is nearer - Auxio's `snap`, on a spring.
     *
     * In a job of its own, which the next touch cancels ([collapseBy]); whatever watches the scroll never
     * runs the settle itself, so interrupting one can never stop the next.
     */
    internal fun settle() {
        val from = offsetPx
        val range = rangePx
        if (from <= 0f || from >= range) return
        val target = if (from < range / 2) 0f else range
        settleJob = scope.launch { animate(from, target) { value, _ -> collapsedPx = value } }
    }

    /** Glides it open - or, [collapsed], collapsed - as a list scrolled for the user takes it along. */
    suspend fun animateTo(collapsed: Boolean) {
        settleJob?.cancel()
        animate(offsetPx, if (collapsed) rangePx else 0f) { value, _ -> collapsedPx = value }
    }

    /**
     * What collapses it, nested directly around the list it stands over - inside anything else the list is
     * nested in, a pull to refresh, so the header has the scroll before any of them: an upward scroll
     * collapses it before the list moves, what a downward one leaves once the list is back at its top opens
     * it - before the list's overscroll stretches - and it settles once the scroll is let go. Nothing while
     * not [isEnabled].
     */
    fun nestedScrollConnection(isEnabled: () -> Boolean = { true }) = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f && isEnabled()) Offset(0f, collapseBy(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f && isEnabled()) Offset(0f, collapseBy(available.y)) else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (isEnabled()) settle()
            return Velocity.Zero
        }
    }
}

/** A [CollapsingHeaderState] for a header that opens open. */
@Composable
fun rememberCollapsingHeaderState(): CollapsingHeaderState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CollapsingHeaderState(scope) }
}

/**
 * A drag on the header [state] collapses, carried on into the [list] it stands over: up, it collapses the
 * header and then scrolls the list; down, it scrolls the list back to its top and then opens the header -
 * Auxio's `ContinuousAppBarLayoutBehavior`. Let go part-way, the header settles as a scroll leaves it.
 */
@Composable
fun Modifier.collapsingHeaderDrag(
    state: CollapsingHeaderState,
    list: () -> ScrollableState?,
    enabled: Boolean = true,
): Modifier {
    val drag = rememberDraggableState { delta ->
        val remaining = delta - state.collapseBy(delta)
        if (remaining != 0f) list()?.dispatchRawDelta(-remaining)
    }
    return draggable(state = drag, orientation = Orientation.Vertical, enabled = enabled, onDragStopped = { state.settle() })
}

/**
 * The header [state] collapses, shrinking in height as it collapses - its content laid out in the height
 * left, which it fills - and fading out over the first half of the collapse. Its height at rest is what
 * [state] collapses through: read while the header is open, from the content laid out at its own height.
 */
fun Modifier.collapsingHeader(state: CollapsingHeaderState): Modifier =
    clipToBounds()
        .layout { measurable, constraints ->
            val placeable = if (state.offsetPx == 0f) {
                measurable.measure(constraints.copy(maxHeight = Constraints.Infinity)).also { state.heightPx = it.height }
            } else {
                val height = (state.heightPx - state.offsetPx.roundToInt()).coerceAtLeast(0)
                measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
            }
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        .graphicsLayer { alpha = 1 - min(state.fraction * 2, 1f) }
