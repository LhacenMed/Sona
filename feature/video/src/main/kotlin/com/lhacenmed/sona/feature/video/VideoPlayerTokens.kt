package com.lhacenmed.sona.feature.video

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Every measure, colour and timing the video player is drawn with, in one place - so each control, rail and
 * bar is built from the same few values and a new one fits in by using them.
 *
 * The player sits over a picture of any colour, so its content is always white on a translucent black scrim,
 * whatever the app's theme; the theme's colours are kept for what stands out - an active control, the seek bar.
 */
internal object VideoPlayerTokens {
    val ContentColor = Color.White

    /** Behind each rail button, so it reads over a bright picture. */
    val ControlScrim = Color.Black.copy(alpha = 0.35f)

    /** Behind the top and bottom bars, fading into the picture. */
    val TopBarScrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))
    val BottomBarScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))

    /** Behind a gesture's feedback in the middle of the screen. */
    val FeedbackScrim = Color.Black.copy(alpha = 0.6f)

    val ControlSize = 44.dp
    val ControlIconSize = 24.dp
    val ControlCornerRadius = 10.dp
    val PlayButtonSize = 52.dp
    val PlayIconSize = 32.dp

    /** Between the screen's edge - past the system bars - and the controls along it. */
    val EdgePadding = 12.dp
    val RailSpacing = 12.dp
    val BarHeight = 56.dp
    val TransportBarHeight = 64.dp

    /** How long the controls stay up untouched while the video plays. */
    const val ControlsTimeoutMs = 4_000L

    /** How long the lock button stays up after a tap on the locked screen. */
    const val LockedHintTimeoutMs = 2_500L

    /** How long a gesture's feedback stays up after the finger lifts. */
    const val FeedbackTimeoutMs = 600L

    /** How often the shown position follows the player while the controls are up. */
    const val PositionTickMs = 250L

    /** How much of a video one drag across the whole screen seeks through - all of a shorter one. */
    const val SeekDragSweepMs = 120_000L

    /** How long the video has to wait for data before the loader shows - so a quick seek never flashes it. */
    const val LoaderDelayMs = 500L

    val ControlsEnter = fadeIn(tween(durationMillis = 180))
    val ControlsExit = fadeOut(tween(durationMillis = 220))
}
