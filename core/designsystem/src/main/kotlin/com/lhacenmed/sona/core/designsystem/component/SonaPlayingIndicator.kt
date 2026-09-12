package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** How tall each bar stands when the indicator is frozen, as a fraction of the icon. */
private val PausedBarHeights = listOf(0.45f, 0.85f, 0.3f)

/** The heights each bar travels between, and how long one full sweep takes. */
private val BarHeightRange = 0.2f..1f
private val BarDurationsMillis = listOf(520, 380, 620)

private const val BAR_COUNT = 3
private const val BAR_WIDTH_FRACTION = 0.22f
private const val BAR_CORNER_FRACTION = 0.4f

/**
 * Three bars that rise and fall while a track plays, and stand still while it is paused.
 *
 * Drawn rather than animated as a frame-by-frame drawable, which is how the reference app does it:
 * three interpolated heights say the same thing as thirty hand-drawn frames, stay sharp at any size,
 * and cost one draw instead of a decode per frame.
 *
 * [isPlaying] only ever stops the motion - the bars keep whatever shape they had - so a pause reads
 * as the music stopping rather than as the row losing its indicator.
 */
@Composable
fun SonaPlayingIndicator(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "playingIndicator")
    val heights = BarDurationsMillis.mapIndexed { index, durationMillis ->
        transition.animateFloat(
            initialValue = BarHeightRange.start,
            targetValue = BarHeightRange.endInclusive,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "playingIndicatorBar$index",
        )
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width * BAR_WIDTH_FRACTION
        // The gaps are whatever the bars leave behind, so the group always spans the icon exactly.
        val gap = (size.width - barWidth * BAR_COUNT) / (BAR_COUNT - 1)
        val corner = CornerRadius(barWidth * BAR_CORNER_FRACTION)

        repeat(BAR_COUNT) { index ->
            val fraction = if (isPlaying) heights[index].value else PausedBarHeights[index]
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                // Grown from the bottom, which is the only edge an equaliser bar stands on.
                topLeft = Offset(x = index * (barWidth + gap), y = size.height - barHeight),
                size = Size(width = barWidth, height = barHeight),
                cornerRadius = corner,
            )
        }
    }
}
