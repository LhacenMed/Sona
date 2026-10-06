package com.lhacenmed.sona.feature.video.surface

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The surface the video's picture is drawn on, handed to the player as it is created - which starts the picture
 * being decoded - and taken back as it leaves, which stops it.
 *
 * A [SurfaceView] rather than a texture: the decoder draws straight onto the display's own layer, with no copy
 * through the app's - the cheapest way to show video, and the only one that keeps HDR and protected content.
 * It follows its layout bounds but no drawing transform, so it is sized by [VideoFrame], never scaled.
 */
@Composable
internal fun VideoSurface(
    onAvailable: (SurfaceView) -> Unit,
    onGone: (SurfaceView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context -> SurfaceView(context).also(onAvailable) },
        onRelease = onGone,
        modifier = modifier,
    )
}
