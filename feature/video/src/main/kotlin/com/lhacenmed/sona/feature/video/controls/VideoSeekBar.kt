package com.lhacenmed.sona.feature.video.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType
import com.lhacenmed.sona.feature.video.formatRemainingTime
import com.lhacenmed.sona.feature.video.formatVideoTime
import com.lhacenmed.sona.feature.video.scrub.VideoScrubState

/** The widest the thumb is drawn, kept for it whatever its size, so the track never moves under the finger. */
private val ThumbSlot = VideoPlayerTokens.ScrubThumbRadius * 2

/**
 * Material's slider, for its dragging, touch target and accessibility, drawn as the player's: a thin track saying how far the
 * video is, swelling under a scrub. Its thumb snaps back to where a scrub began - see [VideoScrubState.dragTo].
 *
 * Everything that moves is read as it is drawn, so a scrub draws this again and composes nothing beyond the slider's own value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoSeekBar(
    position: VideoPosition,
    durationMs: Long,
    scrub: VideoScrubState,
    modifier: Modifier = Modifier,
) {
    val shownMs = scrub.shownPositionMs(position.positionMs)
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val scrubProgress = animateFloatAsState(
        targetValue = if (scrub.isActive) 1f else 0f,
        animationSpec = tween(VideoPlayerTokens.ScrubFadeMs),
        label = "scrubProgress",
    )
    Slider(
        value = fractionOf(shownMs, durationMs),
        onValueChange = { fraction ->
            scrub.begin(position.positionMs)
            // The radii are the finger's, so they are worth more of a longer video.
            val msPerPx = durationMs / scrub.trackBounds.width.coerceAtLeast(1f)
            scrub.dragTo(
                positionMs = (fraction * durationMs).toLong(),
                snapRadiusMs = (with(density) { VideoPlayerTokens.ScrubSnapRadius.toPx() } * msPerPx).toLong(),
                releaseRadiusMs = (with(density) { VideoPlayerTokens.ScrubSnapReleaseRadius.toPx() } * msPerPx).toLong(),
            )
        },
        onValueChangeFinished = scrub::end,
        enabled = durationMs > 0L,
        interactionSource = interactionSource,
        thumb = {
            Canvas(Modifier.size(ThumbSlot)) {
                drawCircle(VideoPlayerTokens.Accent, lerp(VideoPlayerTokens.ThumbRadius, VideoPlayerTokens.ScrubThumbRadius, scrubProgress.value).toPx())
            }
        },
        track = { sliderState ->
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(VideoPlayerTokens.ScrubTrackHeight)
                    .onGloballyPositioned { scrub.trackBounds = it.boundsInRoot() },
            ) {
                val height = lerp(VideoPlayerTokens.SeekTrackHeight, VideoPlayerTokens.ScrubTrackHeight, scrubProgress.value).toPx()
                drawSeekTrack(height, 1f, VideoPlayerTokens.TrackColor)
                drawSeekTrack(height, sliderState.coercedValueAsFraction, VideoPlayerTokens.Accent)
            }
        },
        modifier = modifier,
    )
}

/** How far the video is - or a scrub has reached, as the bar stands. */
@Composable
internal fun VideoElapsedTime(position: VideoPosition, scrub: VideoScrubState, modifier: Modifier = Modifier) {
    PlayerText(text = formatVideoTime(scrub.shownPositionMs(position.positionMs)), style = VideoPlayerType.Time, modifier = modifier)
}

/** How much is left of the video from where the bar stands. */
@Composable
internal fun VideoRemainingTime(
    position: VideoPosition,
    durationMs: Long,
    scrub: VideoScrubState,
    modifier: Modifier = Modifier,
) {
    PlayerText(
        text = formatRemainingTime(durationMs - scrub.shownPositionMs(position.positionMs)),
        style = VideoPlayerType.Time,
        color = VideoPlayerTokens.SecondaryContent,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}

private fun fractionOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/** A pill along the track from its start to [fraction] of it - from the right, where text runs that way. */
internal fun DrawScope.drawSeekTrack(height: Float, fraction: Float, color: Color) {
    val width = size.width * fraction
    drawRoundRect(
        color = color,
        topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f, (size.height - height) / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f),
    )
}
