package com.lhacenmed.sona.core.designsystem.component.screen

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lhacenmed.sona.core.designsystem.component.WindowOverlay
import com.lhacenmed.sona.core.designsystem.component.WindowTouchBlocker
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long an arrived-at row's ripple is held before it fades - long enough to be seen spreading. */
private const val HighlightHoldMillis = 500L

/**
 * The longest a screen waits for each thing its arrival needs - its enter animation to report finishing, the
 * row to be laid out - before going on without it, so a device that never reports one, or a row that never
 * comes, cannot leave the screen's touches blocked.
 */
const val MaxArrivalWaitMillis = 1_000L

/**
 * Ripples the row this answers to as if pressed at its centre - [size] being the row's - and holds the press
 * for [HighlightHoldMillis] before letting it go, so the eye lands on the row a screen has arrived at.
 */
suspend fun MutableInteractionSource.highlightArrival(size: IntSize) {
    val press = PressInteraction.Press(Offset(size.width / 2f, size.height / 2f))
    emit(press)
    delay(HighlightHoldMillis)
    emit(PressInteraction.Release(press))
}

/**
 * Takes the screen's list to the row it marks as playing once the screen is in - [hasEntered] - and ripples
 * the row as if pressed, the way a settings screen arrives at the setting it was opened for. It goes there as
 * the list's scroll button would - see [screenList] - so the header collapses on the way as it does there.
 * Touches are kept from the window until it arrives, so a stray one cannot stop it short. Once only: a screen
 * recreated after it arrived stays where it is. A list marking no row as playing in time is left as it is.
 *
 * Composed inside a [FloatingActionButtonStack][com.lhacenmed.sona.core.designsystem.component.fab.FloatingActionButtonStack],
 * whose lists it reads. The ripple is laid over the row on the window, so no row has to draw it itself.
 */
@Composable
fun PlayingRowReveal(hasEntered: () -> Boolean = { true }) {
    val screenLists = LocalScreenLists.current ?: return
    val latestHasEntered by rememberUpdatedState(hasEntered)
    var hasArrived by rememberSaveable { mutableStateOf(false) }
    var highlightedRow by remember { mutableStateOf(Rect.Zero) }
    val highlight = remember { MutableInteractionSource() }

    if (!hasArrived) WindowTouchBlocker()
    // Composed from the start, empty until the row is known, so the ripple is already listening by the press.
    WindowOverlay {
        val bounds = highlightedRow
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .indication(highlight, ripple()),
        )
    }

    LaunchedEffect(screenLists) {
        if (hasArrived) return@LaunchedEffect
        withTimeoutOrNull(MaxArrivalWaitMillis) { snapshotFlow { latestHasEntered() }.first { it } }
        val target = withTimeoutOrNull(MaxArrivalWaitMillis) {
            snapshotFlow { screenLists.current?.let { list -> list.playingRow()?.let { row -> list to row.index } } }
                .filterNotNull()
                .first()
        }
        if (target == null) {
            hasArrived = true
            return@LaunchedEffect
        }
        val (list, index) = target
        list.scrollToRow(index)
        // Where the row stands once the frame the scroll ended in is laid out - the header's collapse included.
        withFrameNanos {}
        hasArrived = true
        val bounds = list.rowBoundsInWindow(index) ?: return@LaunchedEffect
        highlightedRow = bounds
        withFrameNanos {}
        highlight.highlightArrival(IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()))
    }
}
