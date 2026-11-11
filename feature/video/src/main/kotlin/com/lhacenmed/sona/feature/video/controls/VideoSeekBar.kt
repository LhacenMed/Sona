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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.lerp
import com.lhacenmed.sona.core.designsystem.player.PlayerElapsedTime
import com.lhacenmed.sona.core.designsystem.player.PlayerEndTime
import com.lhacenmed.sona.core.designsystem.player.PlayerTimes
import com.lhacenmed.sona.core.model.VideoChapter
import com.lhacenmed.sona.core.model.indexAt
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.scrub.VideoScrubState

/**
 * Material's slider, for its dragging, touch target and accessibility, drawn as the player's design draws it: a line cut at
 * each of the video's [chapters], and an upright thumb the line is cleared around. The line is filled as far as the video has
 * played; under a scrub the thumb swells and moves alone - the video playing on where it was, the line filled to the thumb in
 * white - every chapter thickens, the one the thumb is over the most, and the video's Most replayed graph - its
 * [replayHeights] - rises over the line, where it has one. Brought back to where the video is, the thumb snaps to it and
 * shrinks back, following the video, until pulled away or moved on after a rest - see [VideoScrubState.dragTo].
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
    chapters: List<VideoChapter>,
    modifier: Modifier = Modifier,
) {
    val shownMs = scrub.shownPositionMs(position.positionMs)
    val chapterStarts = remember(chapters, durationMs) { chapterStartsOf(chapters, durationMs) }
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val scrubProgress = animateFloatAsState(
        targetValue = if (scrub.isActive) 1f else 0f,
        animationSpec = tween(VideoPlayerTokens.SeekMotionMs),
        label = "scrubProgress",
    )
    val snapProgress = animateFloatAsState(
        targetValue = if (scrub.isSnapped) 1f else 0f,
        animationSpec = tween(VideoPlayerTokens.SeekMotionMs),
        label = "snapProgress",
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
                playingMs = position.positionMs,
                snapRadiusMs = (with(density) { VideoPlayerTokens.ScrubSnapRadius.toPx() } * msPerPx).toLong(),
                releaseRadiusMs = (with(density) { VideoPlayerTokens.ScrubSnapReleaseRadius.toPx() } * msPerPx).toLong(),
            )
        },
        onValueChangeFinished = scrub::end,
        enabled = durationMs > 0L,
        interactionSource = interactionSource,
        thumb = {
            Canvas(Modifier.size(VideoPlayerTokens.ScrubThumbSize)) {
                val thumb = thumbSizeOf(scrubProgress.value, snapProgress.value).toSize()
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
                    .height(VideoPlayerTokens.ScrubChapterHeight)
                    .onGloballyPositioned { scrub.trackBounds = it.boundsInRoot() },
            ) {
                val progress = scrubProgress.value
                val thumb = sliderState.coercedValueAsFraction
                // Where the video is, playing on under a scrub - not where the thumb has been taken.
                val playedMs = if (scrub.isActive) position.positionMs else shownMs
                val raisedChapter = chapters.indexAt((thumb * durationMs).toLong()) ?: 0
                val chapterHeight = lerp(VideoPlayerTokens.SeekTrackHeight, VideoPlayerTokens.ScrubTrackHeight, progress).toPx()
                val raisedHeight = lerp(VideoPlayerTokens.SeekTrackHeight, VideoPlayerTokens.ScrubChapterHeight, progress).toPx()
                drawSeekLine(
                    chapterStarts = chapterStarts,
                    played = fractionOf(playedMs, durationMs),
                    scrubbed = thumb.takeIf { scrub.isActive },
                    thumb = thumb,
                    thumbWidth = thumbSizeOf(progress, snapProgress.value).width.toPx(),
                    heightOf = { chapter -> if (chapter == raisedChapter) raisedHeight else chapterHeight },
                )
                val reveal = heatmapReveal.value
                if (replayHeights != null && reveal > 0f) {
                    drawReplayHeatmap(
                        heights = replayHeights,
                        bottom = (size.height - VideoPlayerTokens.ScrubChapterHeight.toPx()) / 2f - VideoPlayerTokens.HeatmapGap.toPx(),
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

/** The thumb, swollen as far as a scrub has [scrubbed] in - and back as far as it has [snapped] to where the video is. */
private fun thumbSizeOf(scrubbed: Float, snapped: Float): DpSize =
    lerp(lerp(VideoPlayerTokens.ThumbSize, VideoPlayerTokens.ScrubThumbSize, scrubbed), VideoPlayerTokens.ThumbSize, snapped)

internal fun fractionOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/** Where each of [chapters] starts along a video [durationMs] long, from 0 to 1 - the first at the start, whenever it begins. */
internal fun chapterStartsOf(chapters: List<VideoChapter>, durationMs: Long): List<Float> =
    chapters.mapIndexed { index, chapter -> if (index == 0) 0f else fractionOf(chapter.startMs, durationMs) }

/**
 * The seek line, every place along it a fraction of it from 0 to 1: a piece for each chapter starting at [chapterStarts] - the
 * whole line, for none - parted by a gap, and cleared around the [thumb], [thumbWidth] wide, where there is one: from every
 * piece it reaches, a chapter's end and the next one's start alike. Each piece, [heightOf] its chapter tall, is filled as far as
 * a scrub has [scrubbed] to, then as far as the video has [played]: from the right where text runs that way.
 */
internal fun DrawScope.drawSeekLine(
    chapterStarts: List<Float>,
    played: Float,
    scrubbed: Float?,
    thumb: Float?,
    thumbWidth: Float,
    heightOf: (chapter: Int) -> Float,
) {
    val width = size.width
    val gap = VideoPlayerTokens.ChapterGap.toPx()
    val clearance = thumbWidth / 2f + VideoPlayerTokens.ThumbClearance.toPx()
    val thumbX = thumb?.times(width)
    val starts = chapterStarts.ifEmpty { listOf(0f) }
    starts.forEachIndexed { chapter, start ->
        val from = start * width + if (chapter == 0) 0f else gap / 2f
        val to = starts.getOrElse(chapter + 1) { 1f } * width - if (chapter == starts.lastIndex) 0f else gap / 2f
        // What is left of the chapter either side of the thumb's clearance - the whole of it where the thumb is not over it.
        val pieces = if (thumbX == null) {
            listOf(from to to)
        } else {
            listOf(from to minOf(to, thumbX - clearance), maxOf(from, thumbX + clearance) to to)
        }
        for ((pieceFrom, pieceTo) in pieces) {
            if (pieceTo - pieceFrom < 1f) continue
            drawSeekPiece(pieceFrom, pieceTo, heightOf(chapter), played * width, scrubbed?.times(width))
        }
    }
}

/** One piece of the seek line, from [from] to [to] - pixels from where the line starts - filled to each place given. */
private fun DrawScope.drawSeekPiece(from: Float, to: Float, height: Float, playedX: Float, scrubbedX: Float?) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val top = (size.height - height) / 2f
    fun left(start: Float, end: Float) = if (isRtl) size.width - end else start
    fun fill(upTo: Float, color: Color) {
        val end = minOf(to, upTo)
        if (end > from) drawRect(color, Offset(left(from, end), top), Size(end - from, height))
    }
    val shape = Path().apply {
        addRoundRect(RoundRect(left(from, to), top, left(from, to) + (to - from), top + height, CornerRadius(height / 2f)))
    }
    clipPath(shape) {
        fill(to, VideoPlayerTokens.SegmentColor)
        scrubbedX?.let { fill(it, VideoPlayerTokens.ScrubbedColor) }
        fill(playedX, VideoPlayerTokens.Accent)
    }
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
