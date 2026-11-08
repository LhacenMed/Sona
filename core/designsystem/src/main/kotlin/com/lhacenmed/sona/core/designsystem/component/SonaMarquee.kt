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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/** How far in from the line's edges the content fades, where it runs past them. */
private val MarqueeFadeWidth = 16.dp

/**
 * [content] on one line, scrolling to show itself whole while it is wider than the line - as [basicMarquee] scrolls it,
 * at its speed, with its pauses, its gap and its passes, but moved where it is laid out rather than only where it is
 * drawn: whatever within it is tapped on its own - one of a credit's artists - is tapped where it is seen, and so is the
 * copy that follows it round.
 *
 * While it is wider than the line, it fades out at the edges it runs past rather than being cut there: always at the
 * end, and at the start as it slides off - faded in as it sets off and out again as the copy comes to rest there.
 *
 * It stands and moves as [text] - what it shows - reads, whichever way the app is laid out: at the line's left and
 * moving left for a name in English, at its right and moving right for one in Arabic. Text reading neither way - numbers
 * alone - follows the app. Filling the line, as a line of text does, so it can stand at either end of it.
 */
@Composable
fun SonaMarquee(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val appDirection = LocalLayoutDirection.current
    val direction = remember(text, appDirection) { readingDirectionOf(text) ?: appDirection }
    val isRtl = direction == LayoutDirection.Rtl
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
            // Laid out as the text reads, so what is at its start and end - a gap after it - is where it reads from and to.
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                content()
                // The copy following the content round, read out once with it rather than twice.
                Box(modifier = Modifier.clearAndSetSemantics {}) { content() }
            }
        },
        modifier = modifier
            .clipToBounds()
            // A layer of its own only while it fades, so the edges take the content out and nothing behind it.
            .graphicsLayer {
                compositingStrategy = if (passDistance.intValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                val distance = passDistance.intValue
                if (distance == 0) return@drawWithContent
                val fade = MarqueeFadeWidth.toPx().coerceAtMost(size.width / 2)
                val leaving = (minOf(offset.value, distance - offset.value) / fade).coerceIn(0f, 1f)
                val start = Color.Black.copy(alpha = 1f - leaving)
                val (left, right) = if (isRtl) Color.Transparent to start else start to Color.Transparent
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to left,
                        fade / size.width to Color.Black,
                        1f - fade / size.width to Color.Black,
                        1f to right,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) { measurables, constraints ->
        val contentConstraints = constraints.copy(maxWidth = Constraints.Infinity)
        val original = measurables[0].measure(contentConstraints)
        val copy = measurables[1].measure(contentConstraints)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.constrainWidth(original.width)
        val distance = if (original.width > width) {
            original.width + with(MarqueeDefaults.Spacing) { calculateSpacing(original.width, width) }
        } else {
            0
        }
        passDistance.intValue = distance
        // Placed by the text's direction rather than the app's: from the line's left edge moving left, or its right moving right.
        layout(width, original.height) {
            val shift = offset.value.roundToInt()
            val x = if (isRtl) width - original.width + shift else -shift
            original.place(x, 0)
            if (distance > 0) copy.place(if (isRtl) x - distance else x + distance, 0)
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
