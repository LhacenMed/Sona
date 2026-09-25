package com.lhacenmed.sona.core.designsystem.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * How much further along its axis than across it a drag has to go to be that axis's - twice, so within
 * about 27° of it. What keeps a sheet's vertical drag off the cover's sideways swipe, and a row's sideways
 * swipe off its list's scroll: a finger is never quite straight, so a drag taking any movement that went
 * far enough its way would take the other's too.
 */
private const val SteepDragMinRatio = 2f

/**
 * Watches [pointerId] a touch slop of movement at a time, and claims the gesture with the first stretch
 * that went steeply along [orientation] - [SteepDragMinRatio] times further along it than across -
 * returning how far that stretch went along it. A shallower stretch is let go and the next one measured
 * afresh, so a drag that turns steep part-way is claimed once it does.
 *
 * Returns null, claiming nothing, for a pointer lifted, or taken by whatever else is listening - a list's
 * scroll, a sheet, a pager. What is laid out around this sees each movement after it does, so every
 * movement not claimed here is let through to them first, and one they took ends the watch: the gesture
 * is theirs, and is never taken back from them part-way.
 */
suspend fun AwaitPointerEventScope.awaitSteepDragSlop(pointerId: PointerId, orientation: Orientation): Float? {
    val touchSlop = viewConfiguration.touchSlop
    var stretch = Offset.Zero
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId } ?: return null
        if (change.changedToUpIgnoreConsumed() || change.isConsumed) return null
        stretch += change.positionChange()
        if (stretch.getDistance() >= touchSlop) {
            val along = if (orientation == Orientation.Vertical) stretch.y else stretch.x
            val across = if (orientation == Orientation.Vertical) stretch.x else stretch.y
            if (abs(along) >= abs(across) * SteepDragMinRatio) {
                change.consume()
                return along
            }
            stretch = Offset.Zero
        }
        awaitPointerEvent(PointerEventPass.Final)
        if (change.isConsumed) return null
    }
}

/**
 * Follows [pointerId], once [awaitSteepDragSlop] has claimed it, to its release - handing [onDrag] each
 * change before consuming it, and consuming every one, across the axis too: once a drag is claimed nothing
 * else moves until the finger lifts. Returns whether it ended in the finger lifting, rather than the
 * pointer being lost.
 */
suspend fun AwaitPointerEventScope.dragUntilRelease(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId } ?: return false
        if (change.changedToUpIgnoreConsumed()) {
            change.consume()
            return true
        }
        onDrag(change)
        change.consume()
    }
}
