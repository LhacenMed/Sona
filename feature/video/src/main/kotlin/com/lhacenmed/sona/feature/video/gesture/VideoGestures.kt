package com.lhacenmed.sona.feature.video.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Which half of the screen a vertical drag runs down - brightness on the left, volume on the right. */
internal enum class ScreenHalf { START, END }

/**
 * What the screen does with each gesture on the video - one left null is not answered.
 *
 * Drags report how far they have gone as a fraction of the screen - across for [onSeekDrag], from where the drag
 * started; up for [onLevelDrag], since the last report - so the screen decides what a full sweep is worth.
 */
internal class VideoGestureActions(
    val onTouch: () -> Unit,
    val onTap: () -> Unit,
    val onDoubleTap: ((zone: TapZone) -> Unit)?,
    val onHoldStart: (() -> Unit)?,
    val onHoldEnd: () -> Unit,
    val onSeekDrag: ((fraction: Float) -> Unit)?,
    val onSeekDragEnd: () -> Unit,
    val onLevelDrag: ((half: ScreenHalf, fraction: Float) -> Unit)?,
    val onLevelDragEnd: () -> Unit,
    val onTransform: ((centroid: Offset, pan: Offset, zoom: Float) -> Unit)?,
)

private enum class DragMode { NONE, SEEK, LEVEL, TRANSFORM, IGNORED }

/**
 * Every gesture the video answers, on the one layer beneath the controls: a control under the finger takes its
 * touch first, and what it takes never reaches the video.
 *
 * One finger taps, double taps, holds or drags; a drag is told apart by its first movement past the touch slop -
 * across seeks, up or down changes a level. A second finger turns it into a zoom and pan until every finger
 * lifts. A tap is cancelled by any drag, and a drag is ignored while a hold is speeding the video up.
 *
 * A tap acts the moment it lifts, never held back to see whether a second follows - the controls answer at once;
 * see [TapSequence] for how a double tap is told apart from it, and in which third of the screen.
 *
 * [actions] is read as each gesture arrives, so the screen hands over new ones without restarting anything.
 */
internal fun Modifier.videoGestures(actions: State<VideoGestureActions>): Modifier =
    pointerInput(Unit) {
        var isHolding = false
        coroutineScope {
            launch {
                // Seen before anything takes it, so every touch and movement - on a control too - keeps the controls up.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        actions.value.onTouch()
                    }
                }
            }
            launch {
                val taps = TapSequence(viewConfiguration.doubleTapTimeoutMillis)
                awaitEachGesture {
                    // A control under the finger takes its own touch, and it never reaches the video.
                    val down = awaitFirstDown()
                    var isCancelled = false
                    val up = if (actions.value.onHoldStart != null) {
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation().also { if (it == null) isCancelled = true }
                        }
                    } else {
                        waitForUpOrCancellation().also { if (it == null) isCancelled = true }
                    }
                    if (isCancelled) return@awaitEachGesture // a drag took it
                    if (up == null) {
                        // Held past the long-press timeout, without moving: sped up until every finger lifts.
                        taps.reset()
                        isHolding = true
                        actions.value.onHoldStart?.invoke()
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        isHolding = false
                        actions.value.onHoldEnd()
                        return@awaitEachGesture
                    }
                    up.consume()
                    val current = actions.value
                    val zone = TapZone.at(up.position.x, size.width.toFloat())
                    when (val outcome = taps.onTap(down.uptimeMillis, up.uptimeMillis, zone, current.onDoubleTap != null)) {
                        TapOutcome.Single -> current.onTap()
                        is TapOutcome.Double -> {
                            if (outcome.undoesTap) current.onTap()
                            current.onDoubleTap?.invoke(outcome.zone)
                        }
                    }
                }
            }
            launch {
                awaitEachGesture {
                    // The tap detector beside this one takes the down, so it is not asked to be untaken.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var mode = DragMode.NONE
                    var half = ScreenHalf.START
                    var travelled = Offset.Zero
                    var seekStartX = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val current = actions.value

                        if (pressed.size > 1 || mode == DragMode.TRANSFORM) {
                            val onTransform = current.onTransform ?: continue
                            if (mode == DragMode.SEEK) current.onSeekDragEnd()
                            if (mode == DragMode.LEVEL) current.onLevelDragEnd()
                            mode = DragMode.TRANSFORM
                            if (pressed.size > 1) {
                                val centroid = event.calculateCentroid() - Offset(size.width / 2f, size.height / 2f)
                                onTransform(centroid, event.calculatePan(), event.calculateZoom())
                            }
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        val change = pressed.single()
                        if (isHolding || mode == DragMode.IGNORED) continue
                        when (mode) {
                            DragMode.NONE -> {
                                // A control under the finger - the seek bar - took the movement for itself.
                                if (change.isConsumed) {
                                    mode = DragMode.IGNORED
                                    continue
                                }
                                travelled += change.positionChange()
                                if (travelled.getDistance() < viewConfiguration.touchSlop) continue
                                val isAcross = abs(travelled.x) > abs(travelled.y)
                                mode = when {
                                    isAcross && current.onSeekDrag != null -> DragMode.SEEK
                                    !isAcross && current.onLevelDrag != null -> DragMode.LEVEL
                                    else -> DragMode.IGNORED
                                }
                                half = if (down.position.x < size.width / 2f) ScreenHalf.START else ScreenHalf.END
                                seekStartX = change.position.x
                                if (mode != DragMode.IGNORED) change.consume()
                            }

                            DragMode.SEEK -> {
                                current.onSeekDrag?.invoke((change.position.x - seekStartX) / size.width)
                                change.consume()
                            }

                            DragMode.LEVEL -> {
                                current.onLevelDrag?.invoke(half, -change.positionChange().y / size.height)
                                change.consume()
                            }

                            DragMode.TRANSFORM, DragMode.IGNORED -> Unit
                        }
                    }
                    when (mode) {
                        DragMode.SEEK -> actions.value.onSeekDragEnd()
                        DragMode.LEVEL -> actions.value.onLevelDragEnd()
                        else -> Unit
                    }
                }
            }
        }
    }
