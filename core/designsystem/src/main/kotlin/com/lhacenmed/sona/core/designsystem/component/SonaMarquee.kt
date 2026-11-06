package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeDefaults
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * [content] on one line, scrolling to show itself whole while it is wider than the line - as [basicMarquee] scrolls it,
 * at its speed, with its pauses, its gap and its passes, but moved where it is laid out rather than only where it is
 * drawn: whatever within it is tapped on its own - one of a credit's artists - is tapped where it is seen, and so is the
 * copy that follows it round.
 */
@Composable
fun SonaMarquee(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    // How far one pass carries the content - its width and the gap after it - while it is wider than the line; 0 while it fits.
    val passDistance = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        // Scrolling whatever the system's animation scale, as basicMarquee does.
        withContext(UnscaledMotion) {
            snapshotFlow { passDistance.intValue }.collectLatest { distance ->
                offset.snapTo(0f)
                if (distance == 0) return@collectLatest
                try {
                    offset.animateTo(distance.toFloat(), marqueePasses(distance, density))
                } finally {
                    offset.snapTo(0f)
                }
            }
        }
    }
    Layout(
        content = {
            content()
            // The copy following the content round, read out once with it rather than twice.
            Box(modifier = Modifier.clearAndSetSemantics {}) { content() }
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val contentConstraints = constraints.copy(maxWidth = Constraints.Infinity)
        val original = measurables[0].measure(contentConstraints)
        val copy = measurables[1].measure(contentConstraints)
        val width = constraints.constrainWidth(original.width)
        val distance = if (original.width > width) {
            original.width + with(MarqueeDefaults.Spacing) { calculateSpacing(original.width, width) }
        } else {
            0
        }
        passDistance.intValue = distance
        layout(width, original.height) {
            val x = -offset.value.roundToInt()
            original.placeRelative(x, 0)
            if (distance > 0) copy.placeRelative(x + distance, 0)
        }
    }
}

/** basicMarquee's passes over [distance] pixels: each at its velocity, after its pause. */
private fun marqueePasses(distance: Int, density: Density): AnimationSpec<Float> {
    val pxPerMilli = with(density) { MarqueeDefaults.Velocity.toPx() } / 1000f
    return repeatable(
        iterations = MarqueeDefaults.Iterations,
        animation = tween(
            durationMillis = ceil(distance / pxPerMilli).toInt(),
            delayMillis = MarqueeDefaults.RepeatDelayMillis,
            easing = LinearEasing,
        ),
    )
}

private object UnscaledMotion : MotionDurationScale {
    override val scaleFactor: Float = 1f
}
