package com.lhacenmed.sona.feature.video.subtitle

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.lhacenmed.sona.core.datastore.SubtitleBackdrop
import com.lhacenmed.sona.core.datastore.SubtitlePosition
import com.lhacenmed.sona.core.datastore.SubtitleTextSize
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.portraitFootMargin
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

/** The shorter side of the phone-sized screen a subtitle's [SubtitleTextSize] is measured against. */
private val ReferenceScreenSide = 390.dp

/** How far past a phone's sizes subtitles grow on a larger screen. */
private const val MaxTextScale = 1.3f

/** How much smaller subtitles are in portrait, where they have the screen's narrow width to fill. */
private const val PortraitTextScale = 0.857f

/** The text's weight where the device can set it - bolder than regular, short of bold. */
private const val SubtitleFontWeight = 600

/** How tall the place for subtitles beneath a letterboxed picture is: two lines, as most are. */
private val BelowPictureHeight = 72.dp
private val BelowPictureGap = 20.dp

/** How subtitles are drawn: as the device's caption settings say, or - while [isCustomized] - as the app's do. */
internal data class SubtitleStyle(
    val isCustomized: Boolean,
    val textSize: SubtitleTextSize,
    val backdrop: SubtitleBackdrop,
    val position: SubtitlePosition,
)

/**
 * Where subtitles stand, the same over any picture, anchored to the screen rather than the frame so a zoomed or cropped
 * picture never takes the lines with it:
 * - in landscape, near the bottom, lifted clear of the controls while they are up and of the resume prompt, and to one side
 *   while [openPanelWidth] takes the other, so the effect of a panel's choices can be seen beside it;
 * - in portrait, beneath a letterboxed picture on the black, as long as there is room above the controls; otherwise as
 *   in landscape;
 * - floating, in the corner of the picture they have, scaled down with it.
 *
 * Their rise is a drawing transform, composing and measuring nothing as it goes.
 */
@Composable
internal fun SubtitleLayer(
    source: SubtitleSource,
    timeline: SubtitleTimeline<Cue>?,
    offsetMs: Long,
    playerCues: StateFlow<List<Cue>>,
    currentPositionMs: () -> Long,
    style: SubtitleStyle,
    isFloating: Boolean,
    areControlsUp: Boolean,
    isResumePromptUp: Boolean,
    openPanelWidth: Dp?,
    pictureBottomPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isPortrait = maxHeight > maxWidth
        val density = LocalDensity.current
        val textScale = (min(maxWidth, maxHeight) / ReferenceScreenSide).coerceAtMost(MaxTextScale) * if (isPortrait) PortraitTextScale else 1f
        val belowPictureTop = with(density) { pictureBottomPx().toDp() } + BelowPictureGap
        val controlsHeight = portraitFootMargin + VideoPlayerTokens.PortraitControlsHeight
        val fitsBelowPicture = isPortrait && !isFloating && belowPictureTop + BelowPictureHeight <= maxHeight - controlsHeight

        val overlay: @Composable (Modifier) -> Unit = { placement ->
            SubtitleOverlay(source, timeline, offsetMs, playerCues, currentPositionMs, style, textScale, startsAtTop = fitsBelowPicture, placement)
        }
        if (fitsBelowPicture) {
            val topPx = with(density) { belowPictureTop.roundToPx() }
            overlay(
                Modifier
                    .offset { IntOffset(0, topPx) }
                    .fillMaxWidth()
                    .height(maxHeight - controlsHeight - belowPictureTop)
                    .padding(horizontal = 24.dp),
            )
        } else {
            val restingBottom = if (isFloating) 4.dp else style.position.bottomDp.dp
            val bottom by animateDpAsState(
                targetValue = when {
                    isFloating -> restingBottom
                    isResumePromptUp && isPortrait -> controlsHeight + VideoPlayerTokens.ResumePromptHeight + VideoPlayerTokens.ResumePromptGap * 2
                    isResumePromptUp -> VideoPlayerTokens.SubtitlesAboveResumePrompt
                    !areControlsUp -> restingBottom
                    isPortrait -> controlsHeight
                    else -> VideoPlayerTokens.SubtitlesAboveControls
                },
                animationSpec = tween(VideoPlayerTokens.SubtitleLiftMs),
                label = "subtitlesBottom",
            )
            val startPadding by animateDpAsState(
                targetValue = when {
                    isFloating -> 8.dp
                    isPortrait -> 24.dp
                    openPanelWidth != null -> 24.dp
                    else -> 120.dp
                },
                animationSpec = tween(VideoPlayerTokens.SubtitleLiftMs),
                label = "subtitlesStart",
            )
            val endPadding by animateDpAsState(
                targetValue = when {
                    isFloating -> 8.dp
                    isPortrait -> 24.dp
                    openPanelWidth != null -> openPanelWidth + VideoPlayerTokens.PanelMargin * 2
                    else -> 120.dp
                },
                animationSpec = tween(VideoPlayerTokens.SubtitleLiftMs),
                label = "subtitlesEnd",
            )
            Box(Modifier.fillMaxSize().padding(start = startPadding, end = endPadding), contentAlignment = Alignment.BottomCenter) {
                overlay(Modifier.fillMaxSize().graphicsLayer { translationY = -bottom.toPx() })
            }
        }
    }
}

/**
 * The lines showing now, in Media3's own view, which draws every kind there is - styled text, positioned text and the
 * images a Blu-ray's subtitles are.
 *
 * A file's lines are followed from the player's position here, [offsetMs] after where the file says they are; the
 * player's own, from [playerCues]. The view is set only as the lines change, so nothing is composed for them. Lines
 * with no place of their own stand at the foot of the view, or - while [startsAtTop] - at its head.
 */
@OptIn(UnstableApi::class)
@Composable
private fun SubtitleOverlay(
    source: SubtitleSource,
    timeline: SubtitleTimeline<Cue>?,
    offsetMs: Long,
    playerCues: StateFlow<List<Cue>>,
    currentPositionMs: () -> Long,
    style: SubtitleStyle,
    textScale: Float,
    startsAtTop: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = remember { SubtitleView(context).apply { setBottomPaddingFraction(0f) } }
    val captionScale = remember { context.getSystemService<CaptioningManager>()?.fontScale ?: 1f }
    val latestPositionMs by rememberUpdatedState(currentPositionMs)
    val latestOffsetMs by rememberUpdatedState(offsetMs)
    val lifecycleOwner = LocalLifecycleOwner.current
    val show: (List<Cue>) -> Unit = { cues -> view.setCues(if (startsAtTop) cues.map(Cue::standingAtTop) else cues) }
    LaunchedEffect(view, source, timeline, playerCues, lifecycleOwner, startsAtTop) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            when {
                source is SubtitleSource.FromFile -> {
                    var shown: List<Cue>? = null
                    while (isActive) {
                        val cues = timeline?.cuesAt((latestPositionMs() - latestOffsetMs) * 1_000L).orEmpty()
                        if (cues != shown) {
                            show(cues)
                            shown = cues
                        }
                        delay(VideoPlayerTokens.SubtitleTickMs)
                    }
                }
                source == SubtitleSource.Off -> view.setCues(emptyList())
                else -> playerCues.collect(show)
            }
        }
    }
    AndroidView(factory = { view }, modifier = modifier, update = { it.applyStyle(style, textScale, captionScale) })
}

@OptIn(UnstableApi::class)
private fun Cue.standingAtTop(): Cue =
    if (bitmap == null && line == Cue.DIMEN_UNSET) {
        buildUpon().setLine(0f, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_START).build()
    } else {
        this
    }

@OptIn(UnstableApi::class)
private fun SubtitleView.applyStyle(style: SubtitleStyle, textScale: Float, captionScale: Float) {
    setApplyEmbeddedStyles(!style.isCustomized)
    setApplyEmbeddedFontSizes(!style.isCustomized)
    if (style.isCustomized) {
        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSize.sp * textScale)
        setStyle(style.backdrop.captionStyle())
    } else {
        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, SubtitleTextSize.Default.sp * textScale * captionScale)
        setUserDefaultStyle()
    }
}

@OptIn(UnstableApi::class)
private fun SubtitleBackdrop.captionStyle(): CaptionStyleCompat {
    val (background, edgeType) = when (this) {
        SubtitleBackdrop.OUTLINE -> Color.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_OUTLINE
        SubtitleBackdrop.SHADOW -> Color.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        SubtitleBackdrop.BOX -> BoxColor to CaptionStyleCompat.EDGE_TYPE_NONE
    }
    return CaptionStyleCompat(Color.WHITE, background, Color.TRANSPARENT, edgeType, Color.BLACK, SubtitleTypeface)
}

private val SubtitleTypeface: Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, SubtitleFontWeight, false) else Typeface.DEFAULT_BOLD

/** Behind a line in a box: black, the picture still showing through. */
private const val BoxColor = 0xB3000000.toInt()
