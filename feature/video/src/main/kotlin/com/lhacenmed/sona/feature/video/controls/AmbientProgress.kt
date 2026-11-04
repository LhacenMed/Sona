package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.feature.video.VideoPlayerTokens

/** A hairline along the foot of the screen saying how far the video is while the controls are away. */
@Composable
internal fun AmbientProgress(position: VideoPosition, durationMs: Long, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawRect(VideoPlayerTokens.AmbientTrackColor)
        val fraction = if (durationMs > 0L) (position.positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val width = size.width * fraction
        drawRect(
            color = VideoPlayerTokens.AmbientFillColor,
            topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f, 0f),
            size = Size(width, size.height),
        )
    }
}
