package com.lhacenmed.sona.core.designsystem.component.swipe

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.gesture.awaitSteepDragSlop
import com.lhacenmed.sona.core.designsystem.motion.RubberBandSettleDurationMillis
import com.lhacenmed.sona.core.designsystem.motion.RubberBandSettleEasing
import com.lhacenmed.sona.core.designsystem.motion.SwipeArmFraction
import com.lhacenmed.sona.core.designsystem.motion.SwipeStretchMaxFraction
import com.lhacenmed.sona.core.designsystem.motion.rubberBandOffset
import com.lhacenmed.sona.core.designsystem.motion.rubberBandPull
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The icon a swipe reveals, at a list row's icon size. */
private val SwipeActionIconSize = 24.dp

/**
 * [content] swiped sideways to act on it - the mini player's swipe, given to a row.
 *
 * Towards a side with an action the row follows the finger up to [SwipeArmFraction] of its width, ticks
 * once there, and resists past it on the rubber band up to [SwipeStretchMaxFraction]; let go past the arm,
 * it runs the action. Towards a side with none it resists from the first pixel, showing there is nothing
 * there. Either way it falls back to rest, as the mini player does.
 *
 * What the row uncovers as it slides is the action's own: its tone's colour across the strip it has left,
 * its icon centred in it and fading in on the way to the arm. It is drawn, not composed, so a swipe
 * recomposes nothing.
 *
 * With no [actions] the row does not swipe, and is laid out exactly as it is with them.
 */
@Composable
fun SwipeActionsBox(
    actions: SwipeActions?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The gesture handler outlives compositions, so it reads the actions as they are when it runs.
    val latestActions by rememberUpdatedState(actions)

    // What each side of the screen uncovers: a row pulled rightwards uncovers its left edge.
    val revealedOnLeft = (if (isRtl) actions?.endToStart else actions?.startToEnd)?.let { revealed(it) }
    val revealedOnRight = (if (isRtl) actions?.startToEnd else actions?.endToStart)?.let { revealed(it) }

    Box(
        modifier = modifier
            .drawBehind {
                val x = offset.value
                val action = (if (x > 0f) revealedOnLeft else revealedOnRight)
                if (x == 0f || action == null) return@drawBehind
                val width = abs(x)
                val left = if (x > 0f) 0f else size.width - width
                val iconSize = SwipeActionIconSize.toPx()
                clipRect(left = left, top = 0f, right = left + width, bottom = size.height) {
                    drawRect(action.containerColor, topLeft = Offset(left, 0f), size = Size(width, size.height))
                    translate(left + (width - iconSize) / 2f, (size.height - iconSize) / 2f) {
                        with(action.icon) {
                            draw(
                                size = Size(iconSize, iconSize),
                                alpha = (width / (size.width * SwipeArmFraction)).coerceIn(0f, 1f),
                                colorFilter = action.iconColorFilter,
                            )
                        }
                    }
                }
            }
            .pointerInput(actions != null, isRtl) {
                if (latestActions == null) return@pointerInput

                fun actionTowards(x: Float): SwipeAction? {
                    val current = latestActions ?: return null
                    return if ((x > 0f) != isRtl) current.startToEnd else current.endToStart
                }

                fun armFor(pull: Float) = if (actionTowards(pull) != null) size.width * SwipeArmFraction else 0f

                fun stretchLimit() = size.width * (SwipeStretchMaxFraction - SwipeArmFraction)

                fun settle() {
                    scope.launch {
                        offset.animateTo(0f, tween(RubberBandSettleDurationMillis, easing = RubberBandSettleEasing))
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Only a near-level swipe moves the row; anything steeper is the list's to scroll.
                    awaitSteepDragSlop(down.id, Orientation.Horizontal) ?: return@awaitEachGesture

                    // Where this gesture has put the row, in px rightwards - held here rather than read
                    // back from the animation, whose snaps are launched and may not have landed yet. Taken
                    // up where a settle still under way has it, so a grab never jumps.
                    var dragOffset = offset.value
                    // The finger's travel, before the rubber band takes its share of it.
                    var dragPull = rubberBandPull(dragOffset, armFor(dragOffset), stretchLimit())
                    // Past the arm, towards an action: letting go now runs it. Unarmed even if grabbed
                    // past the arm, so the tick confirms every action.
                    var isArmed = false

                    try {
                        val isReleased = horizontalDrag(down.id) { change ->
                            // Read before consuming: a consumed change reports no movement.
                            dragPull += change.positionChange().x
                            change.consume()
                            val arm = armFor(dragPull)
                            dragOffset = rubberBandOffset(dragPull, arm, stretchLimit())
                            val reachedArm = arm > 0f && abs(dragOffset) >= arm
                            // Khatmah's tick, as the mini player's: CLOCK_TICK is felt where lighter ones are not.
                            if (reachedArm && !isArmed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            isArmed = reachedArm
                            val targetOffset = dragOffset
                            scope.launch { offset.snapTo(targetOffset) }
                        }
                        if (isReleased && isArmed) actionTowards(dragOffset)?.onSwipe?.invoke()
                    } finally {
                        settle()
                    }
                }
            },
    ) {
        Box(modifier = Modifier.absoluteOffset { IntOffset(offset.value.roundToInt(), 0) }) {
            content()
        }
    }
}

/** A [SwipeAction] as it is drawn: its tone's colours from the theme as it is, and its icon ready to paint. */
private class RevealedAction(
    val containerColor: Color,
    val iconColorFilter: ColorFilter,
    val icon: Painter,
)

@Composable
private fun revealed(action: SwipeAction) = RevealedAction(
    containerColor = action.tone.containerColor,
    iconColorFilter = ColorFilter.tint(action.tone.contentColor),
    icon = rememberVectorPainter(action.icon),
)
