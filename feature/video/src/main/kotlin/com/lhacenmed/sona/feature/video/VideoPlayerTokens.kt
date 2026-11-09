package com.lhacenmed.sona.feature.video

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.designsystem.player.PlayerControls

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
    val SecondaryContent = Color.White.copy(alpha = PlayerControls.SecondaryAlpha)
    val UnchosenContent = Color.White.copy(alpha = 0.78f)
    val TertiaryContent = Color.White.copy(alpha = 0.62f)
    val DimmedContent = Color.White.copy(alpha = 0.5f)

    val TrackColor = Color.White.copy(alpha = 0.24f)
    val AmbientTrackColor = Color.White.copy(alpha = 0.14f)
    val AmbientFillColor = Accent.copy(alpha = 0.85f)
    val RippleColor = Color.White.copy(alpha = 0.09f)

    val TopScrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent))
    val BottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.70f)))

    /**
     * What the portrait foot stands on: deeper, and all the way up its controls, as a picture taller than the room left
     * for it - or zoomed - runs on behind them, however light it is. It fades in over [PortraitScrimFade] above them.
     */
    val PortraitBottomScrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.25f to Color.Black.copy(alpha = 0.6f),
        1f to Color.Black.copy(alpha = 0.85f),
    )
    val PortraitScrimFade = 56.dp

    /** A control that is on, and play while paused: the accent's own dark tone, beneath a light one - see `PlayerCheckedColors`. */
    val CheckedContainer = Color(0xFF5C3B2C)
    val OnCheckedContainer = Color(0xFFFFDBCC)
    val PanelDim = Color.Black.copy(alpha = 0.45f)

    val PanelColor = Color(0xF018181B)

    /**
     * What everything floating over the picture stands on, so it reads over any frame: the landscape transport's buttons,
     * the level meters, the feedback and scrub pills, the resume prompt, the loader and the way to unlock. One ground, so
     * they all read as one set; [GroundPadding] is the room each keeps round what it holds.
     */
    val Ground = Color.Black.copy(alpha = 0.6f)
    val GroundPadding = 8.dp

    /** Play's glyph on the [Accent] it stands on in portrait, below the picture. */
    val OnAccent = Color(0xFF1A1210)
    val ResumePromptDivider = Color.White.copy(alpha = 0.18f)
    val PanelDivider = Color.White.copy(alpha = 0.08f)
    val SegmentGround = Color.White.copy(alpha = 0.07f)
    val SegmentChosen = Color.White.copy(alpha = 0.16f)

    val TopScrimHeight = 112.dp
    val BottomScrimHeight = 140.dp

    /** Every player's bar control - see [PlayerControls]. */
    val BarButtonSize = PlayerControls.BarButtonSize
    val BarIconSize = PlayerControls.BarIconSize

    /** The landscape transport's gap: wider than portrait's, over a wider picture. */
    val LandscapeTransportGap = 40.dp

    /** How far the controls keep from the edge, and how far text keeps to stand in line with the icons of those buttons. */
    val ControlsMargin = 8.dp
    val ControlsTextMargin = ControlsMargin + (BarButtonSize - BarIconSize) / 2

    val SeekTimeGap = 12.dp
    val ElapsedTimeWidth = 40.dp
    val RemainingTimeWidth = 56.dp

    /** How far beneath the safe area's top a pill of feedback stands, clear of the top bar. */
    val PillTopMargin = 68.dp

    /**
     * The portrait layout's foot, as the music player's is: the seek bar and its times beneath it, the transport, then the
     * bar, all on the keyline - the bars' buttons by their bounds - and how far it stands from the bottom of the screen at
     * the least.
     */
    val PortraitKeyline = 24.dp
    val PortraitSeekBarHeight = 32.dp
    val PortraitTimesHeight = 16.dp
    val PortraitTransportTopGap = 16.dp
    val PortraitTransportBottomGap = 24.dp
    val PortraitControlsHeight =
        PortraitSeekBarHeight + PortraitTimesHeight + PortraitTransportTopGap + PlayerControls.PlayButtonSize +
            PortraitTransportBottomGap + BarButtonSize
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

    /**
     * The seek line: cut at each chapter by [ChapterGap], and cleared [ThumbClearance] either side of the upright thumb. Under a
     * scrub every chapter thickens from [SeekTrackHeight] to [ScrubTrackHeight], the one the thumb is over to
     * [ScrubChapterHeight], and the thumb swells from [ThumbSize] to [ScrubThumbSize].
     */
    val SeekTrackHeight = 4.dp
    val ScrubTrackHeight = 6.dp
    val ScrubChapterHeight = 12.dp
    val ChapterGap = 3.dp
    val ThumbClearance = 4.dp
    val ThumbSize = DpSize(4.dp, 20.dp)
    val ScrubThumbSize = DpSize(6.dp, 28.dp)

    /** The seek line's ground, as much of the video as is read ahead, and as far as a scrub has reached. */
    val SegmentColor = Color.White.copy(alpha = 0.22f)
    val BufferedColor = Color.White.copy(alpha = 0.18f)
    val ScrubbedColor = Color.White.copy(alpha = 0.62f)

    /** Under a scrub: the picture dimmed by [ScrubDim], and the top bar faded to [ScrubbingTopBarAlpha]. */
    val ScrubDim = Color.Black.copy(alpha = 0.38f)
    const val ScrubbingTopBarAlpha = 0.35f

    /** The widest the time a scrub has reached stands, with the title of the chapter it is in. */
    val PreviewLabelMaxWidth = 240.dp

    /** The Most replayed graph over the seek track while a scrub is on: how tall it is at its peak, and how far above the track. */
    val HeatmapHeight = 36.dp
    val HeatmapGap = 4.dp
    val HeatmapColor = Color.White.copy(alpha = 0.32f)

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
    const val HeatmapRevealMs = 240
    const val SeekMotionMs = 160
    const val ScrubChromeFadeMs = 180
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
    val Chapter = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
    val PreviewChapter = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
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

/**
 * What the controls keep clear of: [videoSafeArea] - its bottom in landscape alone, where the foot stands on it - its two
 * sides evened out to the wider, so the controls stand centred on the screen, as the picture does, whichever side a
 * cutout or the navigation bar is on.
 */
@Composable
internal fun videoControlsPadding(isLandscape: Boolean): PaddingValues {
    val safeArea = videoSafeArea.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val side = max(safeArea.calculateLeftPadding(layoutDirection), safeArea.calculateRightPadding(layoutDirection))
    return PaddingValues(
        start = side,
        end = side,
        top = safeArea.calculateTopPadding(),
        bottom = if (isLandscape) safeArea.calculateBottomPadding() else 0.dp,
    )
}

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
