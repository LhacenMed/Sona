package com.lhacenmed.sona.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.datastore.VideoAspect
import java.util.Locale

/** "mm:ss", or "h:mm:ss" from the hour on - the way a video player reads out a position. */
internal fun formatVideoTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/** "1.5x" - a speed with no trailing zeros. */
@Composable
internal fun speedLabel(speed: Float): String =
    stringResource(R.string.video_speed_value, speed.toBigDecimal().stripTrailingZeros().toPlainString())

@Composable
internal fun VideoAspect.label(): String =
    stringResource(
        when (this) {
            VideoAspect.CROP -> R.string.video_aspect_crop
            VideoAspect.STRETCH -> R.string.video_aspect_stretch
            VideoAspect.RATIO_16_9 -> R.string.video_aspect_16_9
            VideoAspect.RATIO_18_9 -> R.string.video_aspect_18_9
            VideoAspect.RATIO_4_3 -> R.string.video_aspect_4_3
            VideoAspect.ORIGINAL -> R.string.video_aspect_original
            VideoAspect.FIT -> R.string.video_aspect_fit
        },
    )
