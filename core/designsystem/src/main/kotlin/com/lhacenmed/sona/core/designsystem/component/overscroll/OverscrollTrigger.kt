package com.lhacenmed.sona.core.designsystem.component.overscroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.motion.OverscrollHold
import com.lhacenmed.sona.core.designsystem.motion.RubberBandOverscroll
import com.lhacenmed.sona.core.designsystem.motion.performSwipeArmHaptic
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The gap between a trigger's indicator and the edge of the list it is pulled past. */
private val IndicatorGap = 12.dp

/** The end of a vertical list a trigger is pulled past: its top, or its bottom. */
enum class OverscrollEdge(internal val sign: Float) { Start(1f), End(-1f) }

/**
 * An action pulled for past one [edge] of a list rubber-banded by [overscroll] - a pull to refresh at
 * the top of a library list, the Private Folder at the top of Settings. One per edge: both ends of a list can have
 * their own.
 *
 * The pull stretches the list, as any pull past its end does, and [indicator] hangs just past that edge,
 * coming in with the list in the gap it leaves - how much it shows on the way is its own: see
 * [OverscrollTriggerState.pullFraction] and [ArmedTriggerReveal]. Crossing [threshold] is felt both ways,
 * as a row's swipe crossing its arm is: letting go now triggers, or no longer will. Let go past it,
 * [onTrigger] runs, the list held open under the indicator for as long as it does, then settling back.
 *
 * While [onTrigger] is null nothing triggers and nothing shows - the list rubber-bands as ever - and
 * the layout is the same either way, so turning a trigger on or off never rebuilds the list.
 */
@Composable
fun BoxScope.OverscrollTrigger(
    overscroll: RubberBandOverscroll,
    edge: OverscrollEdge,
    threshold: Dp,
    onTrigger: (suspend () -> Unit)?,
    indicator: @Composable (OverscrollTriggerState) -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val gapPx = with(density) { IndicatorGap.toPx() }
    val scope = rememberCoroutineScope()
    val latestOnTrigger by rememberUpdatedState(onTrigger)
    val trigger = remember(overscroll, edge, scope, thresholdPx, gapPx) {
        OverscrollTriggerState(
            overscroll = overscroll,
            edge = edge,
            scope = scope,
            onTrigger = { latestOnTrigger },
            thresholdPx = thresholdPx,
            gapPx = gapPx,
        )
    }

    val isEnabled = onTrigger != null
    DisposableEffect(trigger, isEnabled) {
        if (isEnabled) trigger.attach()
        onDispose { trigger.detach() }
    }

    val view = LocalView.current
    // Felt only as the finger crosses the point, either way - not as letting go there disarms it.
    LaunchedEffect(trigger) {
        snapshotFlow { trigger.isArmed }.drop(1).collect { isArmed ->
            if (overscroll.isDragged) view.performSwipeArmHaptic(isArmed)
        }
    }

    if (!isEnabled) return
    Box(
        modifier = Modifier
            .align(if (edge == OverscrollEdge.Start) Alignment.TopCenter else Alignment.BottomCenter)
            // Hangs just past the edge, out of sight at rest, so it comes in with the list as it is
            // stretched - and is centred in the gap while the list is held open.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                trigger.indicatorHeightPx = placeable.height
                layout(placeable.width, placeable.height) {
                    val offset = overscroll.stretch - edge.sign * (gapPx + placeable.height)
                    placeable.place(0, offset.roundToInt())
                }
            },
    ) {
        indicator(trigger)
    }
}

/**
 * [content] kept out of sight until [trigger] is armed - then slid in the way the list is pulled, fading
 * in - and taken back out the way it came as the pull falls short again or is let go. So a list stretched
 * by chance shows nothing: what is there is found only by pulling far enough for it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArmedTriggerReveal(trigger: OverscrollTriggerState, content: @Composable () -> Unit) {
    val slideSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    // Pulled down past the top it comes down from above; pulled up past the bottom, up from below.
    val behindEdge = { height: Int -> (-trigger.edge.sign * height).roundToInt() }
    AnimatedVisibility(
        visible = trigger.isArmed,
        enter = slideInVertically(slideSpec, behindEdge) + fadeIn(fadeSpec),
        exit = slideOutVertically(slideSpec, behindEdge) + fadeOut(fadeSpec),
    ) {
        content()
    }
}

/**
 * The trigger a pull past [edge] asks for: armed while a finger holds the list [thresholdPx] or more past
 * it, run as it is let go armed, and holding the list open over its indicator while it runs.
 */
@Stable
class OverscrollTriggerState internal constructor(
    private val overscroll: RubberBandOverscroll,
    val edge: OverscrollEdge,
    private val scope: CoroutineScope,
    private val onTrigger: () -> (suspend () -> Unit)?,
    private val thresholdPx: Float,
    private val gapPx: Float,
) : OverscrollHold {

    /** Whether the action a pull let go armed asked for is running now. */
    var isRunning by mutableStateOf(false)
        private set

    /** How far toward triggering the list is pulled: 0 at rest, 1 where letting go triggers, more past it. */
    val pullFraction: Float
        get() = edge.sign * overscroll.stretch / thresholdPx

    /** Whether letting go now triggers. Never while the action already runs. */
    val isArmed: Boolean
        get() = !isRunning && overscroll.isDragged && pullFraction >= 1f

    /** The indicator's height as last laid out - the list is held open over it, with a gap either side. */
    internal var indicatorHeightPx = 0

    override val restingStretch: Float
        get() = if (isRunning) edge.sign * (indicatorHeightPx + gapPx * 2) else 0f

    override fun onRelease(stretch: Float) {
        if (isRunning || edge.sign * stretch < thresholdPx) return
        val action = onTrigger() ?: return
        isRunning = true
        // Started at once, so an action over at once - opening a screen - is over before the band reads
        // where to settle: it settles straight back to rest, never toward being held open.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                action()
            } finally {
                isRunning = false
            }
            overscroll.settleToRest()
        }
    }

    internal fun attach() {
        when (edge) {
            OverscrollEdge.Start -> overscroll.startHold = this
            OverscrollEdge.End -> overscroll.endHold = this
        }
    }

    internal fun detach() {
        when (edge) {
            OverscrollEdge.Start -> if (overscroll.startHold === this) overscroll.startHold = null
            OverscrollEdge.End -> if (overscroll.endHold === this) overscroll.endHold = null
        }
    }
}
