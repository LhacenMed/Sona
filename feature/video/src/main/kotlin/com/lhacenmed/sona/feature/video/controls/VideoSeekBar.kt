package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
 * Material's slider, for its dragging, touch target and accessibility, drawn as the player's: a thin track with what
 * is buffered and how far the video is, swelling under a scrub with a mark where the scrub began.
 *
 * [scrubProgress] and everything else that moves is read as it is drawn, so a scrub draws this again and composes
 * nothing beyond the slider's own value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoSeekBar(
    position: VideoPosition,
    durationMs: Long,
    scrub: VideoScrubState,
    scrubProgress: State<Float>,
    modifier: Modifier = Modifier,
) {
    val shownMs = scrub.shownPositionMs(position.positionMs)
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = fractionOf(shownMs, durationMs),
        onValueChange = { fraction ->
            scrub.begin(position.positionMs)
            scrub.moveTo((fraction * durationMs).toLong())
        },
        onValueChangeFinished = scrub::end,
        enabled = durationMs > 0L,
        interactionSource = interactionSource,
        thumb = {
            Canvas(Modifier.size(ThumbSlot)) {
                val progress = scrubProgress.value
                val radius = lerp(VideoPlayerTokens.ThumbRadius, VideoPlayerTokens.ScrubThumbRadius, progress).toPx()
                val halo = lerp(VideoPlayerTokens.ThumbHalo, VideoPlayerTokens.ScrubThumbHalo, progress).toPx()
                drawCircle(lerp(VideoPlayerTokens.RestingThumbHalo, VideoPlayerTokens.ScrubbingThumbHalo, progress), radius + halo)
                drawCircle(VideoPlayerTokens.Accent, radius)
            }
        },
        track = { sliderState ->
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(VideoPlayerTokens.ScrubTrackHeight)
                    .onGloballyPositioned { scrub.trackBounds = it.boundsInRoot() },
            ) {
                val progress = scrubProgress.value
                val height = lerp(VideoPlayerTokens.SeekTrackHeight, VideoPlayerTokens.ScrubTrackHeight, progress).toPx()
                drawSeekTrack(height, 1f, VideoPlayerTokens.TrackColor)
                drawSeekTrack(height, fractionOf(position.bufferedMs, durationMs), VideoPlayerTokens.BufferedColor)
                drawSeekTrack(height, sliderState.coercedValueAsFraction, VideoPlayerTokens.Accent)
                if (scrub.isActive) drawOriginTick(fractionOf(scrub.originMs, durationMs), progress)
            }
        },
        modifier = modifier,
    )
}

/** How far the video is: where a scrub began while one is under way, fading back, so the eye has a place to return to. */
@Composable
internal fun VideoElapsedTime(position: VideoPosition, scrub: VideoScrubState, scrubProgress: State<Float>, modifier: Modifier = Modifier) {
    val shownMs = if (scrub.isActive) scrub.originMs else scrub.shownPositionMs(position.positionMs)
    PlayerText(
        text = formatVideoTime(shownMs),
        style = VideoPlayerType.Time,
        color = lerp(VideoPlayerTokens.ContentColor, VideoPlayerTokens.DimmedContent, scrubProgress.value),
        modifier = modifier,
    )
}

/** How much is left of the video from where the bar stands. */
@Composable
internal fun VideoRemainingTime(
    position: VideoPosition,
    durationMs: Long,
    scrub: VideoScrubState,
    scrubProgress: State<Float>,
    modifier: Modifier = Modifier,
) {
    PlayerText(
        text = formatRemainingTime(durationMs - scrub.shownPositionMs(position.positionMs)),
        style = VideoPlayerType.Time,
        color = lerp(VideoPlayerTokens.SecondaryContent, VideoPlayerTokens.DimmedContent, scrubProgress.value),
        modifier = modifier,
    )
}

private fun fractionOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/** A pill along the track from its start to [fraction] of it - from the right, where text runs that way. */
private fun DrawScope.drawSeekTrack(height: Float, fraction: Float, color: Color) {
    val width = size.width * fraction
    drawRoundRect(
        color = color,
        topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f, (size.height - height) / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f),
    )
}

private fun DrawScope.drawOriginTick(fraction: Float, progress: Float) {
    val width = 2.dp.toPx()
    val height = 12.dp.toPx()
    val along = size.width * fraction
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - along else along
    drawRoundRect(
        color = VideoPlayerTokens.OriginTickColor.copy(alpha = VideoPlayerTokens.OriginTickColor.alpha * progress),
        topLeft = Offset(x - width / 2f, (size.height - height) / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2f),
    )
}
