package com.lhacenmed.sona.feature.video.controls

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.lhacenmed.sona.core.designsystem.player.PlayerElapsedTime
import com.lhacenmed.sona.core.designsystem.player.PlayerEndTime
import com.lhacenmed.sona.core.designsystem.player.PlayerTimes
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.scrub.VideoScrubState

/** The widest and tallest the thumb is drawn, kept for it whatever its size, so the track never moves under the finger. */
private val ThumbSlot = VideoPlayerTokens.ScrubThumbSize

/**
 * Material's slider, for its dragging, touch target and accessibility, drawn as every player's: an upright thumb, and a thin
 * track either side of it, kept clear of it, saying how far the video is - all swelling under a scrub. Its thumb snaps back
 * to where a scrub began - see [VideoScrubState.dragTo]. Under a scrub, the video's Most replayed graph - its [replayHeights] -
 * rises over the track, where it has one.
 *
 * Everything that moves is read as it is drawn, so a scrub draws this again and composes nothing beyond the slider's own value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoSeekBar(
    position: VideoPosition,
    durationMs: Long,
    scrub: VideoScrubState,
    replayHeights: FloatArray?,
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
    val heatmapReveal = animateFloatAsState(
        targetValue = if (scrub.isActive && replayHeights != null) 1f else 0f,
        animationSpec = tween(VideoPlayerTokens.HeatmapRevealMs, easing = FastOutSlowInEasing),
        label = "heatmapReveal",
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
                val thumb = lerp(VideoPlayerTokens.ThumbSize, VideoPlayerTokens.ScrubThumbSize, scrubProgress.value).toSize()
                drawRoundRect(
                    color = VideoPlayerTokens.Accent,
                    topLeft = Offset((size.width - thumb.width) / 2f, (size.height - thumb.height) / 2f),
                    size = thumb,
                    cornerRadius = CornerRadius(thumb.width / 2f),
                )
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
                val fraction = sliderState.coercedValueAsFraction
                val gap = VideoPlayerTokens.ThumbGap.toPx() / size.width.coerceAtLeast(1f)
                drawSeekTrack(height, fraction + gap, 1f, VideoPlayerTokens.TrackColor)
                drawSeekTrack(height, 0f, fraction - gap, VideoPlayerTokens.Accent)
                val reveal = heatmapReveal.value
                if (replayHeights != null && reveal > 0f) {
                    drawReplayHeatmap(
                        heights = replayHeights,
                        bottom = (size.height - VideoPlayerTokens.ScrubTrackHeight.toPx()) / 2f - VideoPlayerTokens.HeatmapGap.toPx(),
                        peak = VideoPlayerTokens.HeatmapHeight.toPx() * reveal,
                        alpha = reveal,
                    )
                }
            }
        },
        modifier = modifier,
    )
}

/** How far the video is - or a scrub has reached, as the bar stands. */
@Composable
internal fun VideoElapsedTime(position: VideoPosition, scrub: VideoScrubState, modifier: Modifier = Modifier) {
    PlayerElapsedTime(scrub.shownPositionMs(position.positionMs), VideoPlayerTokens.ContentColor, modifier)
}

/** The time at the seek bar's end, from where the bar stands - see [PlayerEndTime]. */
@Composable
internal fun VideoEndTime(
    position: VideoPosition,
    durationMs: Long,
    scrub: VideoScrubState,
    showsTotalDuration: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerEndTime(scrub.shownPositionMs(position.positionMs), durationMs, showsTotalDuration, onToggle, VideoPlayerTokens.ContentColor, modifier)
}

/** Both times, beneath the seek bar - see [PlayerTimes]. */
@Composable
internal fun VideoTimes(
    position: VideoPosition,
    durationMs: Long,
    scrub: VideoScrubState,
    showsTotalDuration: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerTimes(scrub.shownPositionMs(position.positionMs), durationMs, showsTotalDuration, onToggle, VideoPlayerTokens.ContentColor, modifier)
}

private fun fractionOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/** A pill along the track from its start to [fraction] of it - from the right, where text runs that way. */
internal fun DrawScope.drawSeekTrack(height: Float, fraction: Float, color: Color) = drawSeekTrack(height, 0f, fraction, color)

/** A pill along the track from [from] to [to] of it - nothing where they leave no room - from the right where text runs that way. */
private fun DrawScope.drawSeekTrack(height: Float, from: Float, to: Float, color: Color) {
    val start = size.width * from.coerceIn(0f, 1f)
    val width = size.width * to.coerceIn(0f, 1f) - start
    if (width <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - start - width else start, (size.height - height) / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f),
    )
}

/**
 * The Most replayed graph, rising from [bottom] to [peak] above it where the video is most watched: a smooth curve through
 * the middle of each moment, along the track as it runs - from the right where text runs that way.
 */
private fun DrawScope.drawReplayHeatmap(heights: FloatArray, bottom: Float, peak: Float, alpha: Float) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    fun along(x: Float): Float = if (isRtl) size.width - x else x
    fun x(moment: Int): Float = size.width * (moment + 0.5f) / heights.size
    fun y(moment: Int): Float = bottom - peak * heights[moment]
    val last = heights.lastIndex
    val path = Path().apply {
        moveTo(along(0f), bottom)
        lineTo(along(0f), y(0))
        lineTo(along(x(0)), y(0))
        // Through the middle between each two moments, bent towards the first, so the curve has no corners.
        for (moment in 1..last) {
            quadraticTo(along(x(moment - 1)), y(moment - 1), along((x(moment - 1) + x(moment)) / 2f), (y(moment - 1) + y(moment)) / 2f)
        }
        lineTo(along(x(last)), y(last))
        lineTo(along(size.width), y(last))
        lineTo(along(size.width), bottom)
        close()
    }
    drawPath(path, VideoPlayerTokens.HeatmapColor, alpha = alpha)
}
