package com.lhacenmed.sona.feature.video

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp

/**
 * Every measure, colour and timing the video player is drawn with, in one place - so each control, panel and
 * gesture is built from the same few values, a new one fits in by using them, and retuning one moves them all.
 *
 * The player sits over a picture of any colour, so its content is always white over translucent black, whatever the
 * app's theme; [Accent] is kept for what stands out - the seek bar, a chosen row, a control that is on.
 */
internal object VideoPlayerTokens {
    val Accent = Color(0xFFFF9F6B)
    val ContentColor = Color.White
    val SecondaryContent = Color.White.copy(alpha = 0.72f)
    val UnchosenContent = Color.White.copy(alpha = 0.78f)
    val TertiaryContent = Color.White.copy(alpha = 0.62f)
    val DimmedContent = Color.White.copy(alpha = 0.5f)

    val TrackColor = Color.White.copy(alpha = 0.24f)
    val AmbientTrackColor = Color.White.copy(alpha = 0.14f)
    val AmbientFillColor = Accent.copy(alpha = 0.85f)
    val RippleColor = Color.White.copy(alpha = 0.09f)

    val TopScrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent))
    val BottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.70f)))
    val PanelDim = Color.Black.copy(alpha = 0.45f)

    val PanelColor = Color(0xF018181B)

    /** What the transport's buttons stand on over the picture, so they read over any frame. */
    val TransportGround = Color.Black.copy(alpha = 0.45f)
    val PillColor = Color(0xD118181B)
    val ResumePromptColor = Color(0xEB242428)
    val ResumePromptDivider = Color.White.copy(alpha = 0.18f)
    val PanelDivider = Color.White.copy(alpha = 0.08f)
    val SegmentGround = Color.White.copy(alpha = 0.07f)
    val SegmentChosen = Color.White.copy(alpha = 0.16f)

    val TopScrimHeight = 112.dp
    val BottomScrimHeight = 140.dp

    val BarButtonSize = 44.dp
    val BarIconSize = 22.dp
    val SkipButtonSize = 56.dp
    val SkipIconSize = 28.dp
    val PlayButtonSize = 64.dp
    val PlayIconSize = 38.dp
    val TransportGap = 28.dp

    /** How far the controls keep from the edge, and how far text keeps to stand in line with the icons of those buttons. */
    val ControlsMargin = 8.dp
    val ControlsTextMargin = ControlsMargin + (BarButtonSize - BarIconSize) / 2

    val SeekTimeGap = 12.dp
    val ElapsedTimeWidth = 40.dp
    val RemainingTimeWidth = 56.dp

    /** How far beneath the safe area's top a pill of feedback stands, clear of the top bar. */
    val PillTopMargin = 68.dp

    /** The rows of the portrait layout's foot, and how far they stand from the bottom of the screen at the least. */
    val PortraitSeekRowHeight = 32.dp
    val PortraitControlsHeight = PortraitSeekRowHeight + BarButtonSize
    val PortraitFootMargin = 12.dp

    /** Where subtitles stand above the bottom of the screen while the landscape controls are up, and while the resume prompt is. */
    val SubtitlesAboveControls = 72.dp
    val SubtitlesAboveResumePrompt = 112.dp

    val ResumePromptHeight = 36.dp
    val ResumePromptGap = 8.dp

    /** How near where a scrub began the thumb has to come to snap back there, and how far it is pulled to come away again. */
    val ScrubSnapRadius = 12.dp
    val ScrubSnapReleaseRadius = 20.dp

    val ScrubPillWidth = 248.dp
    val ScrubPillCornerRadius = 16.dp

    val SeekTrackHeight = 4.dp
    val ScrubTrackHeight = 6.dp
    val ThumbRadius = 7.dp
    val ScrubThumbRadius = 10.dp

    /** The most of the screen's width and height a scrubbing preview takes, the picture's own shape within it. */
    val PreviewWidth = 176.dp
    val PreviewHeight = 99.dp
    val PreviewCornerRadius = 12.dp
    val PreviewBorder = 1.5.dp

    /** How far above the seek track the preview stands, and how close to the screen's edge it may come. */
    val PreviewGap = 18.dp
    val PreviewEdgeMargin = 8.dp

    val PanelMargin = 12.dp
    val PanelCornerRadius = 20.dp
    val TracksPanelWidth = 440.dp
    val ListPanelWidth = 320.dp
    val PanelRowHeight = 40.dp
    val PanelRowCornerRadius = 10.dp

    val MeterTrackWidth = 6.dp
    val MeterTrackHeight = 140.dp
    val MeterEdgeMargin = 28.dp

    /** How much of the screen's height the ripple of a double tap spans, and how far past the edge its centre lies. */
    const val RippleHeightRatio = 1.436f
    const val RippleCentreOutsideRatio = 0.051f

    /** How long the controls stay up untouched while the video plays. */
    const val ControlsTimeoutMs = 4_000L

    /** How long the unlock button stays up after a tap on the locked screen. */
    const val LockedHintTimeoutMs = 2_500L

    /** How long the resume prompt stays up once the video plays. */
    const val ResumePromptTimeoutMs = 6_000L
    const val ResumePromptFadeMs = 240

    /** How long a gesture's feedback stays up after the finger lifts. */
    const val FeedbackTimeoutMs = 600L

    /** How often the shown position follows the player. */
    const val PositionTickMs = 250L

    /** How often a subtitle file's lines are matched to the player's position - closer than the eye can tell. */
    const val SubtitleTickMs = 50L

    /** How much of a video one drag across the whole screen seeks through - all of a shorter one. */
    const val SeekDragSweepMs = 120_000L

    /** How long the video has to wait for data before the loader shows - so a quick seek never flashes it. */
    const val LoaderDelayMs = 500L

    /** A step of the subtitle sync, and how a held button repeats it. */
    const val SyncStepMs = 100L
    const val SyncRepeatDelayMs = 400L
    const val SyncRepeatMs = 80L

    const val ChromeFadeMs = 220
    const val ScrubFadeMs = 90
    const val SubtitleLiftMs = 220
    const val MeterFadeMs = 120

    val ControlsEnter = fadeIn(tween(durationMillis = ChromeFadeMs))
    val ControlsExit = fadeOut(tween(durationMillis = ChromeFadeMs))
}

/** The player's type sizes. Set in the app's own font - see [PlayerText]. */
internal object VideoPlayerType {
    private val Tabular = "tnum"

    val Title = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, letterSpacing = (-0.01).em)
    val Time = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = Tabular)
    val Speed = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular)
    val PreviewTime = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular)
    val MeterValue = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular)
    val SeekAmount = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular)
    val PanelRow = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val PanelSection = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em)
    val PanelLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val PanelSegment = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular)
    val Pill = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val PromptLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
    val PromptTime = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp, fontFeatureSettings = Tabular)
}

/**
 * What the player's content keeps clear of, as if the system bars were always shown, so nothing moves as they hide and
 * come back. Whatever lines up with the controls stands within this, rather than in the middle of the whole screen.
 */
@OptIn(ExperimentalLayoutApi::class)
internal val videoSafeArea: WindowInsets
    @Composable get() = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)

/** How far the portrait layout's controls stand from the bottom of the screen - clear of the navigation bar, however tall. */
internal val portraitFootMargin: Dp
    @Composable get() = max(VideoPlayerTokens.PortraitFootMargin, videoSafeArea.asPaddingValues().calculateBottomPadding())

/** Text in one of [VideoPlayerType]'s sizes and the font the user chose for the app. */
@Composable
internal fun PlayerText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = VideoPlayerTokens.ContentColor,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.merge(style),
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
