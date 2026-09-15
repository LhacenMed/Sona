package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val BAR_COUNT = 3

private val BarWidth = 4.dp

private val BarSpacing = 6.dp

/** ArchiveTune's `ThumbnailCornerRadius`, which its indicator rounds its bars with. */
private val BarCornerRadius = 10.dp

private val BarsHeight = 24.dp

/** The height a bar rests at while its track is not playing, as a share of the bars' full height. */
private const val RESTING_HEIGHT_FRACTION = 0.1f

/** How long the indicator takes to fade in as its track becomes the current one, and out as it stops being it. */
private const val FADE_MILLIS = 500

/**
 * Bars that leap to random heights while [isPlaying] - ArchiveTune's `PlayingIndicator` - settle to rest
 * when it stops, and hold still once they are no longer [isActive].
 *
 * Playing, each bar springs to a random height between its resting height and the whole, rests 50ms and
 * goes again, each on its own, so the bars never move in step. Stopped, each springs down to its resting
 * height and stays there. Either way a bar sets off from wherever it is at that moment, so starting and
 * stopping never make it jump. No longer active - its track replaced by another while it fades out - a
 * bar simply stops where it is, so the only bars moving are the ones fading in.
 */
@Composable
private fun PlayingIndicatorBars(
    isActive: Boolean,
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatables =
        remember {
            List(BAR_COUNT) {
                Animatable(RESTING_HEIGHT_FRACTION)
            }
        }

    // Restarted on every change: cancelling the effect it replaces stops each bar where it is, and the
    // new one carries on from there - or, once inactive, leaves it there.
    LaunchedEffect(isActive, isPlaying) {
        if (!isActive) return@LaunchedEffect
        animatables.forEach { animatable ->
            launch {
                if (isPlaying) {
                    while (true) {
                        animatable.animateTo(
                            Random.nextFloat() * (1f - RESTING_HEIGHT_FRACTION) + RESTING_HEIGHT_FRACTION,
                        )
                        delay(50)
                    }
                } else {
                    animatable.animateTo(RESTING_HEIGHT_FRACTION)
                }
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(BarSpacing),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier,
    ) {
        animatables.forEach { animatable ->
            Canvas(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(BarWidth),
            ) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x = 0f, y = size.height * (1 - animatable.value)),
                    size = size.copy(height = animatable.value * size.height),
                    cornerRadius = CornerRadius(BarCornerRadius.toPx()),
                )
            }
        }
    }
}

/**
 * What the current track's cover shows - ArchiveTune's `PlayingIndicatorBox`, keeping only its bars.
 *
 * Fades in over half a second when its track becomes the current one and out when it stops being it.
 * The bars leap while that track plays and settle to rest while it is paused. When another track takes
 * its place, its bars stop where they are and only fade, so the only bars moving are the new track's,
 * rising as they fade in.
 */
@Composable
fun SonaPlayingIndicatorBox(
    isActive: Boolean,
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(tween(FADE_MILLIS)),
        exit = fadeOut(tween(FADE_MILLIS)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier,
        ) {
            PlayingIndicatorBars(
                // Read inside the fade, which keeps composing while it fades out - so an indicator on its
                // way out already sees that its track is no longer the current one, and its bars stop.
                isActive = isActive,
                isPlaying = isPlaying,
                color = color,
                modifier = Modifier.height(BarsHeight),
            )
        }
    }
}
