package com.lhacenmed.sona.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.datastore.VideoAspect
import com.lhacenmed.sona.core.datastore.VideoOrientation
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.core.designsystem.player.formatPlaybackTime
import com.lhacenmed.sona.feature.playback.MediaTrack
import java.util.Locale

/** How far a scrub has gone, always with its sign: "+16:28". */
internal fun formatSeekDelta(deltaMs: Long): String =
    (if (deltaMs < 0L) Minus else "+") + formatPlaybackTime(kotlin.math.abs(deltaMs))

/** How far double taps have sought: "\u221210 s". */
internal fun formatSeekSeconds(deltaMs: Long): String =
    "${if (deltaMs < 0L) Minus else "+"}${kotlin.math.abs(deltaMs) / 1_000L} s"

/** How far subtitles are moved: "0.0 s", or with its sign, "+0.5 s". */
internal fun formatSubtitleOffset(offsetMs: Long): String {
    val sign = if (offsetMs == 0L) "" else if (offsetMs < 0L) Minus else "+"
    return sign + String.format(Locale.US, "%.1f s", kotlin.math.abs(offsetMs) / 1_000.0)
}

private const val Minus = "\u2212"

/** "1.5x" - a speed with no trailing zeros. */
@Composable
internal fun speedLabel(speed: Float): String =
    stringResource(R.string.video_speed_value, speed.toBigDecimal().stripTrailingZeros().toPlainString())

/**
 * What a track of a file is called: the name the file gives it or its language, how many channels it has where that
 * tells two apart, and whether it is forced. The [index]th of its kind, where the file says nothing of it.
 */
@Composable
internal fun MediaTrack.displayName(index: Int): String {
    val name = label ?: language?.let { Locale.forLanguageTag(it).displayLanguage.ifEmpty { it } } ?: stringResource(R.string.video_track_number, index + 1)
    val channels = when (channelCount) {
        1 -> stringResource(R.string.video_track_mono)
        2 -> stringResource(R.string.video_track_stereo)
        6 -> "5.1"
        8 -> "7.1"
        else -> null
    }
    val forced = if (isForced) stringResource(R.string.video_track_forced) else null
    return listOfNotNull(name, channels, forced).joinToString(" \u00B7 ")
}

/** What each fitting is called - said, as the app's toast, as it is chosen. */
@get:StringRes
internal val VideoAspect.labelRes: Int
    get() = when (this) {
        VideoAspect.CROP -> R.string.video_aspect_crop
        VideoAspect.STRETCH -> R.string.video_aspect_stretch
        VideoAspect.RATIO_16_9 -> R.string.video_aspect_16_9
        VideoAspect.RATIO_18_9 -> R.string.video_aspect_18_9
        VideoAspect.RATIO_4_3 -> R.string.video_aspect_4_3
        VideoAspect.ORIGINAL -> R.string.video_aspect_original
        VideoAspect.FIT -> R.string.video_aspect_fit
    }

/** What each way of standing is called - the rotation button's name, and said, as the app's toast, as it is chosen. */
@get:StringRes
internal val VideoOrientation.labelRes: Int
    get() = when (this) {
        VideoOrientation.AUTO -> R.string.video_rotation_auto
        VideoOrientation.PORTRAIT -> R.string.video_rotation_portrait
        VideoOrientation.LANDSCAPE -> R.string.video_rotation_landscape
    }

/** The rotation button's icon for each way of standing, so it shows which the player is set to. */
internal val VideoOrientation.icon: ImageVector
    get() = when (this) {
        VideoOrientation.AUTO -> PlayerIcons.AutoRotate
        VideoOrientation.PORTRAIT -> PlayerIcons.OrientationPortrait
        VideoOrientation.LANDSCAPE -> PlayerIcons.OrientationLandscape
    }
