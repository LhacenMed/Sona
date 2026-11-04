package com.lhacenmed.sona.feature.video.scrub

import android.graphics.Bitmap
import android.view.SurfaceView
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType
import com.lhacenmed.sona.feature.video.capture.copyFrameInto
import com.lhacenmed.sona.feature.video.formatSeekDelta
import com.lhacenmed.sona.feature.video.formatVideoTime
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * The frame the video has reached and the time it is at, standing over the seek bar's thumb for as long as a scrub is
 * under way - placed as it is laid out, so it follows the finger on every frame with nothing composed for it.
 *
 * Its picture is taken from the video's own [surface], which the player is already scrubbing through as fast as its
 * hardware decoder can: no second decoder and no reading of the file, for any format the player plays. Left out while
 * there is no picture to take - a video played for its sound shows the times alone.
 */
@Composable
internal fun ScrubPreview(
    scrub: VideoScrubState,
    surface: () -> SurfaceView?,
    showsPicture: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pictureSize = remember(videoWidth, videoHeight) { previewPictureSize(videoWidth, videoHeight) }
    val edgeMargin = with(density) { VideoPlayerTokens.PreviewEdgeMargin.toPx() }
    val gap = with(density) { VideoPlayerTokens.PreviewGap.toPx() }
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
            visible = scrub.isActive && durationMs > 0L,
            enter = fadeIn(tween(VideoPlayerTokens.ScrubFadeMs)) + scaleIn(tween(VideoPlayerTokens.ScrubFadeMs), initialScale = 0.92f),
            exit = fadeOut(tween(VideoPlayerTokens.ScrubFadeMs)) + scaleOut(tween(VideoPlayerTokens.ScrubFadeMs), targetScale = 0.92f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showsPicture) PreviewPicture(surface, pictureSize)
                PreviewTimes(scrub)
            }
        }
    }
}

@Composable
private fun PreviewPicture(surface: () -> SurfaceView?, size: DpSize) {
    val shape = RoundedCornerShape(VideoPlayerTokens.PreviewCornerRadius)
    val pixels = with(LocalDensity.current) { IntSize(size.width.roundToPx(), size.height.roundToPx()) }
    val latestSurface by rememberUpdatedState(surface)
    val frames = remember { PreviewFrames() }
    LaunchedEffect(pixels) {
        // Two to take turns with: each frame is written to the one not showing, then shown.
        val buffers = List(2) { PreviewBuffer(pixels) }
        var next = 0
        while (true) {
            val buffer = buffers[next]
            if (latestSurface()?.copyFrameInto(buffer.bitmap) == true) {
                frames.shown = buffer.image
                next = 1 - next
            }
            delay(VideoPlayerTokens.PreviewFrameMs)
        }
    }
    Canvas(
        modifier = Modifier
            .size(size)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(Color.Black)
            .border(VideoPlayerTokens.PreviewBorder, Color.White.copy(alpha = 0.9f), shape),
    ) {
        frames.shown?.let { drawFrame(it) }
    }
}

private fun DrawScope.drawFrame(image: ImageBitmap) {
    drawImage(
        image = image,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )
}

@Composable
private fun PreviewTimes(scrub: VideoScrubState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerText(formatVideoTime(scrub.targetMs), VideoPlayerType.PreviewTime)
        PlayerText(formatSeekDelta(scrub.targetMs - scrub.originMs), VideoPlayerType.Time, color = VideoPlayerTokens.SecondaryContent)
    }
}

/** The frame showing, read as the preview is drawn: a new one draws it again and composes nothing. */
private class PreviewFrames {
    var shown by mutableStateOf<ImageBitmap?>(null)
}

private class PreviewBuffer(pixels: IntSize) {
    val bitmap: Bitmap = Bitmap.createBitmap(pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
    val image: ImageBitmap = bitmap.asImageBitmap()
}

/** The picture's own shape, as large as fits the preview's bounds; the bounds themselves before the shape is known. */
private fun previewPictureSize(videoWidth: Int, videoHeight: Int): DpSize {
    val maxWidth = VideoPlayerTokens.PreviewWidth
    val maxHeight = VideoPlayerTokens.PreviewHeight
    if (videoWidth <= 0 || videoHeight <= 0) return DpSize(maxWidth, maxHeight)
    val ratio = videoWidth.toFloat() / videoHeight
    return if (maxWidth / maxHeight > ratio) DpSize(maxHeight * ratio, maxHeight) else DpSize(maxWidth, maxWidth / ratio)
}
