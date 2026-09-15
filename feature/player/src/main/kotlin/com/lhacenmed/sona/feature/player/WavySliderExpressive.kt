package com.lhacenmed.sona.feature.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

private const val SemanticsProgressStep = 0.01f

/** A seek bar whose track waves while playing and whose thumb narrows to a line while dragged. Ported from ArchiveTune. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WavySliderExpressive(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    onValueCommit: (Float) -> Unit,
    enabled: Boolean,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    thumbColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    trackEdgePadding: Dp = thumbRadius,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f,
    thumbLineHeightWhenInteracting: Dp = 24.dp,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackEdgePaddingPx = with(density) { trackEdgePadding.coerceAtLeast(0.dp).toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }

    val stroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }

    val normalizedValueState = remember(valueRange) {
        derivedStateOf {
            val v = value()
            if (valueRange.endInclusive == valueRange.start) 0f
            else ((v - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        }
    }

    val semanticNormalizedValueState = remember {
        derivedStateOf {
            val norm = normalizedValueState.value
            ((norm / SemanticsProgressStep).roundToInt() * SemanticsProgressStep).coerceIn(0f, 1f)
        }
    }
    val semanticSliderValueState = remember(valueRange) {
        derivedStateOf {
            valueRange.start + semanticNormalizedValueState.value * (valueRange.endInclusive - valueRange.start)
        }
    }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueCommit by rememberUpdatedState(onValueCommit)
    var isPointerSeeking by remember { mutableStateOf(false) }
    val isInteracting = isPointerSeeking

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim",
    )
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (enabled && isPlaying && !isInteracting) 1f else 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude",
    )

    val currentHalfWidth = remember(thumbRadius, strokeWidth) {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val radius = thumbRadius
            val halfStroke = strokeWidth * 0.6f
            radius * (1f - fraction) + halfStroke * fraction
        }
    }

    val dynamicGapSize = remember {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val idleGap = 6.dp
            val draggingGap = currentHalfWidth.value + 1.2.dp
            idleGap + (draggingGap - idleGap) * fraction
        }
    }

    val renderedNormalizedProgress = remember {
        val initialVal = value()
        val initialNorm = if (valueRange.endInclusive == valueRange.start) 0f
            else ((initialVal - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        mutableFloatStateOf(initialNorm)
    }
    var lastProgressUpdateNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isInteracting, enabled) {
        snapshotFlow { normalizedValueState.value }.collect { target ->
            if (!enabled || isInteracting) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collect
            }

            val start = renderedNormalizedProgress.floatValue
            if (abs(start - target) > 0.1f) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collect
            }

            val nowNanos = System.nanoTime()
            val intervalMs = if (lastProgressUpdateNanos == 0L) {
                180L
            } else {
                ((nowNanos - lastProgressUpdateNanos) / 1_000_000L).coerceIn(1L, 250L)
            }
            lastProgressUpdateNanos = nowNanos

            if (abs(start - target) <= 0.0001f) {
                renderedNormalizedProgress.floatValue = target
                return@collect
            }

            val durationNanos = (intervalMs * 900_000L).coerceAtLeast(1_000_000L)
            var startFrameNanos = 0L
            while (isActive) {
                val frameNanos = withFrameNanos { it }
                if (startFrameNanos == 0L) startFrameNanos = frameNanos
                val elapsedNanos = (frameNanos - startFrameNanos).coerceAtLeast(0L)
                val fraction = (elapsedNanos.toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
                renderedNormalizedProgress.floatValue = start + (target - start) * fraction
                if (fraction >= 1f) break
            }
            renderedNormalizedProgress.floatValue = target
        }
    }

    val containerHeight = max(WavyProgressIndicatorDefaults.LinearContainerHeight, max(thumbRadius * 2, thumbLineHeightWhenInteracting))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clearAndSetSemantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = semanticSliderValueState.value,
                    range = valueRange.start..valueRange.endInclusive,
                    steps = 0,
                )
                if (enabled) {
                    setProgress { requested ->
                        val coerced = requested.coerceIn(valueRange.start, valueRange.endInclusive)
                        latestOnValueChange(coerced)
                        latestOnValueCommit(coerced)
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        LinearWavyProgressIndicator(
            progress = { renderedNormalizedProgress.floatValue },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = trackEdgePadding.coerceAtLeast(0.dp))
                .clearAndSetSemantics { },
            color = activeTrackColor,
            trackColor = inactiveTrackColor,
            stroke = stroke,
            trackStroke = stroke,
            gapSize = 2f * dynamicGapSize.value * (1.0f + 0.1573f * animatedAmplitude * animatedAmplitude),
            stopSize = 3.dp,
            amplitude = { progress -> if (progress > 0f) animatedAmplitude else 0f },
            wavelength = wavelength,
            waveSpeed = waveSpeed,
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
            val trackStart = edgePaddingPx
            val trackEnd = size.width - edgePaddingPx
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val thumbY = size.height / 2
            val renderedProgress = renderedNormalizedProgress.floatValue

            fun lerp(start: Float, stop: Float, fraction: Float): Float {
                return start + (stop - start) * fraction
            }

            val currentWidth = lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.2f, thumbInteractionFraction)
            val currentHeight = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
            val rawThumbX = trackStart + (trackWidth * renderedProgress)
            val minThumbCenter = (currentWidth / 2f).coerceAtMost(size.width / 2f)
            val maxThumbCenter = (size.width - currentWidth / 2f).coerceAtLeast(minThumbCenter)
            val thumbX = rawThumbX.coerceIn(minThumbCenter, maxThumbCenter)

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(
                    thumbX - currentWidth / 2f,
                    thumbY - currentHeight / 2f,
                ),
                size = Size(currentWidth, currentHeight),
                cornerRadius = CornerRadius(currentWidth / 2f),
            )
        }

        val haptics = LocalHapticFeedback.current
        val currentHaptics = rememberUpdatedState(haptics)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, valueRange, trackEdgePaddingPx) {
                    if (!enabled) return@pointerInput

                    fun valueForX(rawX: Float): Float {
                        val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                        val trackStart = edgePadding
                        val trackEnd = size.width - edgePadding
                        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                        val normalized = ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
                        return valueRange.start +
                            normalized * (valueRange.endInclusive - valueRange.start)
                    }

                    val lastHapticStep = intArrayOf(-1)

                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPointerSeeking = true
                            down.consume()
                            var latestGestureValue = valueForX(down.position.x)
                            latestOnValueChange(latestGestureValue)

                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull { it.pressed }
                                    ?: break

                                pointerId = change.id
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }

                                if (change.position != change.previousPosition) {
                                    change.consume()
                                    latestGestureValue = valueForX(change.position.x)
                                    val quantized = (latestGestureValue.coerceIn(0f, 1f) * 20f).toInt()
                                    if (quantized != lastHapticStep[0]) {
                                        lastHapticStep[0] = quantized
                                        currentHaptics.value.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    latestOnValueChange(latestGestureValue)
                                }
                            }

                            latestOnValueCommit(latestGestureValue)
                        } finally {
                            isPointerSeeking = false
                        }
                    }
                },
        )
    }
}

/** A position sampled on a steady beat while playing, and the fraction of [totalDuration] it is. Ported from ArchiveTune. */
@Composable
internal fun rememberSmoothProgress(
    isPlayingProvider: () -> Boolean,
    currentPositionProvider: () -> Long,
    totalDuration: Long,
    sampleWhilePlayingMs: Long = 200L,
): Pair<State<Float>, State<Long>> {
    var sampledPosition by remember { mutableLongStateOf(0L) }
    var sampledFraction by remember { mutableFloatStateOf(0f) }

    val latestPositionProvider by rememberUpdatedState(newValue = currentPositionProvider)
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)

    val safeUpperBound = totalDuration.coerceAtLeast(0L)
    val safeDuration = totalDuration.coerceAtLeast(1L)

    LaunchedEffect(totalDuration) {
        fun sampleNow() {
            val clampedPosition = latestPositionProvider().coerceIn(0L, safeUpperBound)
            sampledPosition = clampedPosition
            sampledFraction = (clampedPosition / safeDuration.toFloat()).coerceIn(0f, 1f)
        }

        sampleNow()

        while (isActive) {
            if (!latestIsPlayingProvider()) {
                val initialPos = latestPositionProvider()
                snapshotFlow {
                    latestIsPlayingProvider() || latestPositionProvider() != initialPos
                }.first { it }

                sampleNow()
                if (!latestIsPlayingProvider()) {
                    continue
                }
            }

            delay(sampleWhilePlayingMs)
            sampleNow()
        }
    }

    val fractionState = remember {
        derivedStateOf { sampledFraction }
    }

    val displayedPositionState = remember(totalDuration) {
        derivedStateOf {
            sampledPosition.coerceIn(0L, totalDuration.coerceAtLeast(0L))
        }
    }

    return fractionState to displayedPositionState
}
