package com.lhacenmed.sona.core.designsystem.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed

/**
 * Keeps every finger with whatever drag claimed it until it lifts, across everything laid out inside -
 * what [awaitSteepDragSlop] promises for its own drags, made true of all of them.
 *
 * A scrollable that lost a finger to another - a list to the pager's sideways swipe - waits to take it
 * back, and Compose hands it over the moment one of the finger's events reaches it unconsumed. The drag
 * holding the finger consumes only the events that move it, so a finger resting for an instant - an event
 * that moves nowhere - is enough: the list takes the finger, measuring all it travelled since it went
 * down, the pager's swipe is cancelled, and the finger scrolls the list from there.
 *
 * So once any movement of a finger has been consumed - some drag has claimed it - every later event of it
 * is consumed here as well, in the final pass, which reaches this before anything inside it: nothing is
 * left over for a waiting scrollable to take. Its lift is left as it is, so the drag holding it still ends
 * - and flings - as it would.
 */
fun Modifier.holdClaimedFingers(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val claimed = HashSet<PointerId>()
        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { change ->
                when {
                    !change.pressed -> Unit
                    change.id in claimed -> change.consume()
                    change.isConsumed && change.positionChangeIgnoreConsumed() != Offset.Zero -> claimed += change.id
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
