package com.lhacenmed.sona.feature.video.scrub

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lhacenmed.sona.core.designsystem.player.formatPlaybackTime
import com.lhacenmed.sona.feature.video.videoControlsPadding
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType
import com.lhacenmed.sona.core.model.VideoChapter
import com.lhacenmed.sona.core.model.indexAt
import com.lhacenmed.sona.feature.video.controls.chapterStartsOf
import com.lhacenmed.sona.feature.video.controls.drawSeekLine
import com.lhacenmed.sona.feature.video.controls.fractionOf
import com.lhacenmed.sona.feature.video.formatSeekDelta
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The frames a scrub reaches, from a player of their own - see `VideoScrubPreviewPlayer`. Started on a view, they show on it
 * until it leaves the screen.
 */
internal class ScrubFrames(
    val onStart: (TextureView, positionMs: Long) -> Unit,
    val onMove: (positionMs: Long) -> Unit,
)

/**
 * The frame a scrub has reached and the time it is at, standing over the seek bar's thumb while [isVisible] - and over its Most
 * replayed graph, [isAboveHeatmap] - placed as it is laid out, so it follows the finger on every frame with nothing composed for it.
 */
@Composable
internal fun ScrubPreview(
    scrub: VideoScrubState,
    frames: ScrubFrames,
    isVisible: Boolean,
    isAboveHeatmap: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    durationMs: Long,
    chapters: List<VideoChapter>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pictureSize = remember(videoWidth, videoHeight) { previewPictureSize(videoWidth, videoHeight) }
    val edgeMargin = with(density) { VideoPlayerTokens.PreviewEdgeMargin.toPx() }
    val gap = with(density) {
        VideoPlayerTokens.PreviewGap.toPx() + if (isAboveHeatmap) (VideoPlayerTokens.HeatmapHeight + VideoPlayerTokens.HeatmapGap).toPx() else 0f
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val track = scrub.trackBounds
                    val fraction = if (durationMs > 0L) (scrub.targetMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    val thumbX = if (isRtl) track.right - track.width * fraction else track.left + track.width * fraction
                    placeable.place(
                        IntOffset(
                            previewLeft(thumbX, placeable.width.toFloat(), constraints.maxWidth.toFloat(), edgeMargin).roundToInt(),
                            (track.top - placeable.height - gap).roundToInt(),
                        ),
                    )
                }
            },
    ) {
        AnimatedVisibility(
            visible = isVisible && durationMs > 0L,
            enter = fadeIn(tween(VideoPlayerTokens.ScrubFadeMs)) + scaleIn(tween(VideoPlayerTokens.ScrubFadeMs), initialScale = 0.92f),
            exit = fadeOut(tween(VideoPlayerTokens.ScrubFadeMs)) + scaleOut(tween(VideoPlayerTokens.ScrubFadeMs), targetScale = 0.92f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewPicture(scrub, frames, pictureSize)
                PreviewTimes(scrub, chapters)
            }
        }
    }
}

@Composable
private fun PreviewPicture(scrub: VideoScrubState, frames: ScrubFrames, size: DpSize) {
    val shape = RoundedCornerShape(VideoPlayerTokens.PreviewCornerRadius)
    val latestFrames by rememberUpdatedState(frames)
    LaunchedEffect(scrub) {
        snapshotFlow { scrub.targetMs }.distinctUntilChanged().collect { latestFrames.onMove(it) }
    }
    AndroidView(
        // A texture rather than a surface: this one is clipped to its corners and fades with the preview.
        factory = { context -> TextureView(context).also { latestFrames.onStart(it, scrub.targetMs) } },
        modifier = Modifier
            .size(size)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(Color.Black)
            .border(VideoPlayerTokens.PreviewBorder, Color.White.copy(alpha = 0.9f), shape),
    )
}

/** The time a scrub has reached, and the title of the chapter it is in. */
@Composable
private fun PreviewTimes(scrub: VideoScrubState, chapters: List<VideoChapter>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = VideoPlayerTokens.PreviewLabelMaxWidth),
    ) {
        PlayerText(formatPlaybackTime(scrub.targetMs), VideoPlayerType.PreviewTime)
        chapterTitleAt(chapters, scrub.targetMs)?.let { title ->
            PlayerText(title, VideoPlayerType.PreviewChapter, color = VideoPlayerTokens.SecondaryContent)
        }
    }
}

/** The title of the chapter [positionMs] is in - null for a video without chapters. */
private fun chapterTitleAt(chapters: List<VideoChapter>, positionMs: Long): String? = chapters.indexAt(positionMs)?.let { chapters[it].title }

/**
 * A scrub by a swipe with the controls away, which it leaves away: a pill beneath where the top bar would be saying where the
 * finger has taken the video, out of how long, how far that is from where it was, the chapter it is in, and where that lies
 * along the video.
 */
@Composable
internal fun ScrubPill(scrub: VideoScrubState, isVisible: Boolean, durationMs: Long, chapters: List<VideoChapter>, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(VideoPlayerTokens.ScrubFadeMs)),
        exit = fadeOut(tween(VideoPlayerTokens.ScrubFadeMs)),
        // Clear of the system bars and the cutout alike on either side, so it stands in the middle of the screen.
        modifier = modifier
            .padding(videoControlsPadding(isLandscape = false))
            .padding(top = VideoPlayerTokens.PillTopMargin),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .width(VideoPlayerTokens.ScrubPillWidth)
                .background(VideoPlayerTokens.Ground, RoundedCornerShape(VideoPlayerTokens.ScrubPillCornerRadius))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                PlayerText(formatPlaybackTime(scrub.targetMs), VideoPlayerType.PreviewTime)
                PlayerText(" / ${formatPlaybackTime(durationMs)}", VideoPlayerType.Time, color = VideoPlayerTokens.SecondaryContent)
                Spacer(Modifier.weight(1f))
                PlayerText(formatSeekDelta(scrub.targetMs - scrub.originMs), VideoPlayerType.SeekAmount)
            }
            chapterTitleAt(chapters, scrub.targetMs)?.let { PlayerText(it, VideoPlayerType.Pill) }
            val chapterStarts = remember(chapters, durationMs) { chapterStartsOf(chapters, durationMs) }
            Canvas(Modifier.fillMaxWidth().height(VideoPlayerTokens.SeekTrackHeight)) {
                drawSeekLine(
                    chapterStarts = chapterStarts,
                    played = fractionOf(scrub.targetMs, durationMs),
                    scrubbed = null,
                    thumb = null,
                    thumbWidth = 0f,
                    heightOf = { size.height },
                )
            }
        }
    }
}

/** The picture's own shape, as large as fits the preview's bounds; the bounds themselves before the shape is known. */
private fun previewPictureSize(videoWidth: Int, videoHeight: Int): DpSize {
    val maxWidth = VideoPlayerTokens.PreviewWidth
    val maxHeight = VideoPlayerTokens.PreviewHeight
    if (videoWidth <= 0 || videoHeight <= 0) return DpSize(maxWidth, maxHeight)
    val ratio = videoWidth.toFloat() / videoHeight
    return if (maxWidth / maxHeight > ratio) DpSize(maxHeight * ratio, maxHeight) else DpSize(maxWidth, maxWidth / ratio)
}
