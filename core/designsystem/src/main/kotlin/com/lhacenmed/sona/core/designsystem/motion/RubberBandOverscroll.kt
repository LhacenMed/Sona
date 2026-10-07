package com.lhacenmed.sona.core.designsystem.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import com.lhacenmed.sona.core.designsystem.effect.SonaEffects
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** How far past its end a list can be stretched, as a share of its length - the cover pager's reach. */
private const val ListStretchReach = 0.3f

/** Less than this, of a pixel or a pixel a second, is no movement at all. */
private const val MovementThreshold = 0.5f

/**
 * The pace a released band runs at: its own, whatever Android's animator duration scale is - the band
 * answers a touch, as Android's own stretch does, which that scale leaves alone - and none while the
 * app's animations are turned off, when it is back at rest at once.
 */
private object ReleasePace : MotionDurationScale {
    override val scaleFactor: Float
        get() = if (SonaEffects.areAnimationsDisabled) 0f else 1f
}

/**
 * Every scrolling list in [content] rubber-banded at its ends in place of Android's stretch - see
 * [RubberBandOverscroll]. Given once, at the root of each window: the dialogs and sheets opened over it
 * inherit it with the rest of the composition.
 */
@Composable
fun ProvideRubberBandOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOverscrollFactory provides RubberBandOverscrollFactory, content = content)
}

/**
 * A [RubberBandOverscroll] held here, for a list whose stretch something beside it follows - the fast
 * scroller's thumb - handed to the list as its `overscrollEffect`.
 */
@Composable
fun rememberRubberBandOverscroll(): RubberBandOverscroll = remember { RubberBandOverscroll() }

private data object RubberBandOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = RubberBandOverscroll()
}

/**
 * Holds a band let go past its start open, rather than letting it settle to rest - a pull to refresh,
 * over its indicator while it refreshes.
 */
interface StartHold {
    /** A finger let the band go [stretch] past its start - a fling's bounce is never this. */
    fun onRelease(stretch: Float)

    /** Where the band comes to rest past its start now: 0, unless held open. */
    val restingStretch: Float
}

/**
 * A list's ends as a rubber band: past either end the content follows the finger with a resistance that
 * grows the farther it goes - [rubberBandOffset], the curve every band in the app is held by - and only
 * ever approaches [ListStretchReach] of the list's length.
 *
 * - **Dragged past an end**, it stretches; dragged back, it gives the stretch back before the list moves.
 * - **Let go while stretched**, it settles back as every released band does, and the list does not fling.
 * - **Flung into an end**, the speed the list still had carries the band out and back on a critically
 *   damped spring. At rest the curve's slope is 1, so the content leaves the edge at the list's speed.
 * - **Caught while settling or bouncing**, it stops where it is and the new drag takes it from there: a
 *   release is the band's own, and a drag ends it.
 * - **Held open past its start** by a [startHold] - a pull to refresh, over its indicator while it
 *   refreshes - it settles there instead of at rest, and back to rest once it is let go of.
 *
 * A band stretches along one axis for as long as it is stretched: which one is only decided at rest.
 *
 * Drawn by moving the list's content on its own layer inside the list's clip: a frame of stretch moves a
 * layer, measuring and drawing nothing again.
 */
@Stable
class RubberBandOverscroll internal constructor() : OverscrollEffect {

    /** The finger travel past the end, signed as the scroll is; 0 at rest. The band shows [stretch]. */
    private var pull by mutableFloatStateOf(0f)

    /** Which way the list scrolls - known from its first scroll, and only ever read while stretched. */
    private var isHorizontal = false

    private var widthPx = 0
    private var heightPx = 0

    /** The settle or bounce under way, which a drag stops wherever it has got to. */
    private var releaseJob: Job? = null

    /**
     * Whether the band rests open where a [startHold] holds it - stretched, but not in progress: a list
     * held open for a refresh answers a touch as any list at rest does, a tap as a tap and a sideways
     * swipe as the pager's, rather than the band taking the finger straight away.
     */
    private var isRestingOpen = false

    /** What holds the band open past its start when it is let go there, if anything does. */
    var startHold: StartHold? = null

    /** What holds the band open past its end when it is let go there, if anything does - see [startHold]. */
    var endHold: StartHold? = null

    /** Whether a finger is moving the list - and so the band, once it is past an end - right now. */
    var isDragged by mutableStateOf(false)
        private set

    /** The farthest the band can hold the content, 0 until the list is measured. */
    private val stretchLimit: Float
        get() = (if (isHorizontal) widthPx else heightPx) * ListStretchReach

    /**
     * How far past its end the band holds the content now, in pixels: positive past its start - the top
     * of a vertical list - and negative past its end.
     */
    val stretch: Float
        get() = rubberBandOffset(pull, 0f, stretchLimit)

    override val isInProgress: Boolean
        get() = pull != 0f && !isRestingOpen

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (source == NestedScrollSource.UserInput) {
            releaseJob?.cancel()
            isDragged = true
        }
        isRestingOpen = false
        if (pull == 0f) {
            if (delta.x != 0f) isHorizontal = true else if (delta.y != 0f) isHorizontal = false
        }
        val scroll = delta.along()

        // A stretched band gives back first whatever moves toward rest, and never past it.
        val released = when {
            pull == 0f || sign(scroll) == sign(pull) -> 0f
            abs(scroll) >= abs(pull) -> -pull
            else -> scroll
        }
        pull += released

        val consumed = performScroll(axisOffset(scroll - released))
        // What the list had no room for stretches the band - a drag's only: a fling's is taken whole, as
        // the velocity it ends with.
        val leftover = scroll - released - consumed.along()
        if (source == NestedScrollSource.UserInput && abs(leftover) > MovementThreshold && stretchLimit > 0f) {
            pull += leftover
        }
        return axisOffset(released) + consumed
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        isDragged = false
        if (pull != 0f) {
            performFling(Velocity.Zero)
            val hold = if (pull > 0f) startHold else endHold
            hold?.onRelease(stretch)
            release { settle(to = hold?.restingStretch ?: 0f) }
            return
        }
        val remaining = velocity.along() - performFling(velocity).along()
        if (abs(remaining) > MovementThreshold && stretchLimit > 0f) release { bounce(remaining) }
    }

    /**
     * Runs [animation] as the band's release, at [ReleasePace], waiting for it - unless a drag stops it
     * first.
     */
    private suspend fun release(animation: suspend () -> Unit) = coroutineScope {
        releaseJob = launch(ReleasePace) { animation() }
    }

    /**
     * Settles a band open past its start to where its [startHold] now has it rest - back to rest, once a
     * refresh it was held open for is over. A band a finger is moving is left to the finger: it settles
     * there when let go.
     */
    suspend fun settleToRest() {
        if (isDragged || pull <= 0f) return
        release { settle(to = startHold?.restingStretch ?: 0f) }
    }

    /**
     * To [to] from wherever the band was let go - rest, or where a [startHold] holds it open - the way
     * every released band settles.
     */
    private suspend fun settle(to: Float) {
        isRestingOpen = false
        animate(
            initialValue = stretch,
            targetValue = to,
            animationSpec = tween(RubberBandSettleDurationMillis, easing = RubberBandSettleEasing),
        ) { value, _ -> pull = rubberBandPull(value, 0f, stretchLimit) }
        isRestingOpen = to != 0f
    }

    /**
     * Out and back from rest, carried by [velocity] - the fling's own, as it hit the end. Critically
     * damped, so it returns without swinging past rest, in about the time a settle takes.
     */
    private suspend fun bounce(velocity: Float) {
        animate(
            initialValue = 0f,
            targetValue = 0f,
            initialVelocity = velocity,
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        ) { value, _ -> pull = value }
    }

    private fun Offset.along(): Float = if (isHorizontal) x else y

    private fun Velocity.along(): Float = if (isHorizontal) x else y

    private fun axisOffset(value: Float): Offset = if (isHorizontal) Offset(value, 0f) else Offset(0f, value)

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            widthPx = placeable.width
            heightPx = placeable.height
            return layout(placeable.width, placeable.height) {
                // Read in the layer's block, so the stretch changing updates the layer alone.
                placeable.placeWithLayer(0, 0) {
                    val stretch = stretch
                    translationX = if (isHorizontal) stretch else 0f
                    translationY = if (isHorizontal) 0f else stretch
                }
            }
        }

        // A list leaving mid-release takes its release with it; it comes back at rest.
        override fun onDetach() {
            pull = 0f
            isRestingOpen = false
            isDragged = false
        }
    }
}
