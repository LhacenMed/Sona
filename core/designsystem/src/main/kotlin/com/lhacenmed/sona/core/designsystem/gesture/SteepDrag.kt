package com.lhacenmed.sona.core.designsystem.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
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
 * afresh, so a drag that turns steep part-way is claimed once it does, unless something else has taken
 * it first.
 *
 * Returns null, claiming nothing, for a pointer lifted, or taken first by whatever else is listening -
 * a list's scroll, a sheet, a pager.
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
    }
}
