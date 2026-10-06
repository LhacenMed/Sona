package com.lhacenmed.sona.feature.video.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.lhacenmed.sona.core.datastore.VideoAspect
import kotlin.math.roundToInt

/** How far two fingers may enlarge the video. */
private const val MaxZoom = 4f

/**
 * How far the video is zoomed and panned - by two fingers, while Zoom pan is on.
 *
 * Read only as the frame is laid out, so zooming lays the frame out again without composing anything.
 */
@Stable
internal class VideoZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    // The frame and the screen as last laid out - plain fields, so learning them never lays the frame out again.
    private var frame = Size.Zero
    private var screen = Size.Zero

    /**
     * Zooms by [zoom] about [centroid] - the point between the fingers, from the screen's centre - and pans by
     * [pan], the frame never leaving a gap at the screen's edge.
     */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(1f, MaxZoom)
        val appliedZoom = newScale / scale
        // How far the frame will overflow the screen on each side at the new zoom - how far it can be panned.
        val bounds = Offset(
            ((frame.width * appliedZoom - screen.width) / 2f).coerceAtLeast(0f),
            ((frame.height * appliedZoom - screen.height) / 2f).coerceAtLeast(0f),
        )
        val moved = (offset - centroid) * appliedZoom + centroid + pan
        scale = newScale
        offset = Offset(moved.x.coerceIn(-bounds.x, bounds.x), moved.y.coerceIn(-bounds.y, bounds.y))
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun onLaidOut(frame: Size, screen: Size) {
        this.frame = frame
        this.screen = screen
    }
}

/**
 * Lays [content] - the video's surface - out at the size [aspect] fits it to, for a picture [videoWidth] by
 * [videoHeight], then [zoom]ed and centred on the screen with the overflow cut off.
 *
 * Before the picture's size is known it fills the screen, so the surface is there for the first frame.
 */
@Composable
internal fun VideoFrame(
    aspect: VideoAspect,
    videoWidth: Int,
    videoHeight: Int,
    zoom: VideoZoomState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val screen = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val frame = frameSize(aspect, screen, videoWidth, videoHeight) * zoom.scale
        zoom.onLaidOut(frame, screen)
        val width = frame.width.roundToInt()
        val height = frame.height.roundToInt()
        val placeable = measurables.single().measure(Constraints.fixed(width, height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(
                x = ((constraints.maxWidth - width) / 2f + zoom.offset.x).roundToInt(),
                y = ((constraints.maxHeight - height) / 2f + zoom.offset.y).roundToInt(),
            )
        }
    }
}

/** The size [aspect] gives a picture [videoWidth] by [videoHeight] pixels on a [screen] that size. */
private fun frameSize(aspect: VideoAspect, screen: Size, videoWidth: Int, videoHeight: Int): Size {
    if (videoWidth <= 0 || videoHeight <= 0) return screen
    val videoRatio = videoWidth.toFloat() / videoHeight
    return when (aspect) {
        VideoAspect.FIT -> screen.fitting(videoRatio)
        VideoAspect.CROP -> screen.covering(videoRatio)
        VideoAspect.STRETCH -> screen
        VideoAspect.ORIGINAL -> Size(videoWidth.toFloat(), videoHeight.toFloat())
        VideoAspect.RATIO_16_9, VideoAspect.RATIO_18_9, VideoAspect.RATIO_4_3 -> screen.fitting(checkNotNull(aspect.ratio))
    }
}

/** The largest size of [ratio] that fits within this one. */
private fun Size.fitting(ratio: Float): Size =
    if (width / height > ratio) Size(height * ratio, height) else Size(width, width / ratio)

/** The smallest size of [ratio] that covers this one. */
private fun Size.covering(ratio: Float): Size =
    if (width / height > ratio) Size(width, width / ratio) else Size(height * ratio, height)
