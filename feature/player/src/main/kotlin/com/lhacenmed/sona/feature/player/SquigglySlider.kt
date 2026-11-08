/*
 * Ported from saket/squiggly-slider 1.0.0 (Apache License 2.0), the slider behind ArchiveTune's circular seek bar.
 */

package com.lhacenmed.sona.feature.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

private const val SegmentsPerWavelength = 10
private const val TwoPi = 2 * PI.toFloat()
private const val SquigglesLoopDurationMillis = 4_000

/**
 * ```
 *
 *       _....._                                     _....._         ▲
 *    ,="       "=.                               ,="       "=.   amplitude
 *  ,"             ".                           ,"             ".    │
 *,"                 ".,                     ,,"                 "., ▼
 *""""""""""|""""""""""|."""""""""|""""""""".|""""""""""|""""""""""|
 *                       ".               ."
 *                         "._         _,"
 *                            "-.....-"
 *◀─────────────── Wavelength ──────────────▶
 *
 * ```
 */
@Immutable
internal data class SquigglesSpec(
    val strokeWidth: Dp = 4.dp,
    val wavelength: Dp = (strokeWidth * 6).coerceAtLeast(16.dp),
    val amplitude: Dp = (strokeWidth / 2).coerceAtLeast(2.dp),
)

/**
 * A slider whose played part is a travelling wave that flattens while dragged - Material's own upright thumb between
 * the wave and the rest of the track, each kept [PlayerSeekThumbGap] clear of it, as Material's flat track is.
 *
 * Built here rather than taken from the published artifact: that artifact calls a material3 `Slider`
 * overload which now survives only as a hidden binary-compatibility stub, one that drops `valueRange` -
 * so a track's position, measured in milliseconds, was always drawn at the end of a 0..1 range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChangeFinished: () -> Unit,
    colors: SliderColors,
    squigglesSpec: SquigglesSpec,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val animationProgress = rememberSquigglesAnimationProgress()

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        valueRange = valueRange,
        thumb = { SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, thumbSize = PlayerSeekThumbSize) },
        track = { sliderState ->
            SquigglyTrack(
                interactionSource = interactionSource,
                sliderState = sliderState,
                colors = colors,
                squigglesSpec = squigglesSpec,
                animationProgress = animationProgress,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SquigglyTrack(
    interactionSource: MutableInteractionSource,
    sliderState: SliderState,
    colors: SliderColors,
    squigglesSpec: SquigglesSpec,
    animationProgress: State<Float>,
) {
    val sliderHeight = (squigglesSpec.amplitude + squigglesSpec.strokeWidth) * 2
    val inactiveTrackColor = colors.inactiveTrackColor
    val activeTrackColor = colors.activeTrackColor

    val isDragged by interactionSource.collectIsDraggedAsState()
    val animatedAmplitude by animateDpAsState(
        targetValue = if (isDragged) 0.dp else squigglesSpec.amplitude,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "Squiggles amplitude",
    )

    Spacer(
        Modifier
            .fillMaxWidth()
            .height(sliderHeight)
            .drawWithCache {
                val path = Path()
                val pathStyle =
                    Stroke(
                        width = squigglesSpec.strokeWidth.toPx(),
                        join = StrokeJoin.Round,
                        cap = StrokeCap.Round,
                        pathEffect =
                            PathEffect.cornerPathEffect(
                                radius = squigglesSpec.wavelength.toPx(), // For slightly smoother waves.
                            ),
                    )
                onDrawBehind {
                    // Each part ends where Material's flat track would: the thumb's half and the gap short of its centre.
                    val thumbWidth = PlayerSeekThumbSize.width.toPx()
                    val clearance = thumbWidth / 2 + PlayerSeekThumbGap.toPx()
                    val strokeWidth = squigglesSpec.strokeWidth.toPx()
                    val thumbCentre = thumbWidth / 2 + (size.width - thumbWidth) * sliderState.valueFraction
                    val playedEnd = thumbCentre - clearance
                    val restStart = thumbCentre + clearance
                    val isRtl = layoutDirection == LayoutDirection.Rtl
                    // Laid out from the left, then mirrored where text runs the other way.
                    withTransform({ if (isRtl) scale(scaleX = -1f, scaleY = 1f, pivot = center) }) {
                        if (restStart + strokeWidth / 2 < size.width) {
                            drawLine(
                                color = inactiveTrackColor,
                                start = Offset(restStart + strokeWidth / 2, center.y),
                                end = Offset(size.width - strokeWidth / 2, center.y),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round,
                            )
                        }
                        if (playedEnd > 0f) {
                            path.rewind()
                            buildSquiggles(
                                path = path,
                                squigglesSpec = squigglesSpec.copy(amplitude = animatedAmplitude),
                                startOffset = Offset(0f, center.y),
                                endOffset = Offset(playedEnd, center.y),
                                animationProgress = animationProgress.value,
                            )
                            // Clipped, as its rounded cap would otherwise reach past where it ends.
                            clipRect(right = playedEnd) {
                                drawPath(
                                    path = path,
                                    color = activeTrackColor,
                                    style = pathStyle,
                                )
                            }
                        }
                    }
                }
            },
    )
}

@Composable
private fun rememberSquigglesAnimationProgress(): State<Float> =
    rememberInfiniteTransition(label = "Infinite squiggles").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = SquigglesLoopDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "Squiggles",
    )

/** Maths copied from [squigglyspans](https://github.com/samruston/squigglyspans). */
private fun DrawScope.buildSquiggles(
    path: Path,
    squigglesSpec: SquigglesSpec,
    startOffset: Offset,
    endOffset: Offset,
    animationProgress: Float,
) {
    val waveStartOffset = startOffset.x + (squigglesSpec.strokeWidth.toPx() / 2)
    val waveEndOffset = (endOffset.x - (squigglesSpec.strokeWidth.toPx() / 2)).coerceAtLeast(waveStartOffset)

    val segmentWidth = squigglesSpec.wavelength.toPx() / SegmentsPerWavelength
    val numOfPoints = ceil((waveEndOffset - waveStartOffset) / segmentWidth).toInt() + 1

    var pointX = waveStartOffset
    for (point in 0..numOfPoints) {
        val proportionOfWavelength = (pointX - waveStartOffset) / squigglesSpec.wavelength.toPx()
        val radiansX = proportionOfWavelength * TwoPi + (TwoPi * animationProgress)
        val offsetY = center.y + (sin(radiansX) * squigglesSpec.amplitude.toPx())

        if (point == 0) path.moveTo(pointX, offsetY) else path.lineTo(pointX, offsetY)
        pointX = (pointX + segmentWidth).coerceAtMost(waveEndOffset)
    }
}

/** The 0..1 share of its range the slider's value stands at. */
@OptIn(ExperimentalMaterial3Api::class)
private val SliderState.valueFraction: Float
    get() {
        val start = valueRange.start
        val end = valueRange.endInclusive
        return if (end - start == 0f) 0f else ((value.coerceIn(start, end) - start) / (end - start)).coerceIn(0f, 1f)
    }
