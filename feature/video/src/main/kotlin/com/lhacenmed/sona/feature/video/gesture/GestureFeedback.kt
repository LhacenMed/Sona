package com.lhacenmed.sona.feature.video.gesture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.feature.video.formatSeekSeconds
import com.lhacenmed.sona.feature.video.speedLabel
import com.lhacenmed.sona.feature.video.videoControlsPadding
import com.lhacenmed.sona.feature.video.videoSafeArea
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a gesture, or a control with nothing else to show for it, tells the user it did. */
internal sealed interface GestureFeedback {
    /** Sought [deltaMs] by double taps - added up for as long as they keep coming the same way. */
    data class DoubleTapSeek(val deltaMs: Long) : GestureFeedback

    data class Brightness(val level: Float) : GestureFeedback

    /** The volume, 0 to [MaxVolumeLevel]: the device's own, then boosted past its loudest. */
    data class Volume(val level: Float) : GestureFeedback

    data class Speed(val speed: Float) : PillFeedback
}

/** The loudest a volume drag reaches: 200%, the device's loudest boosted by up to +20 dB - see `VideoPlayerViewModel.volumeBoost`. */
internal const val MaxVolumeLevel = 2f

/** Feedback with no place of its own, shown on a pill beneath the top bar, clear of the transport. */
internal sealed interface PillFeedback : GestureFeedback

/** The feedback showing now: held while a gesture is under way, and let go a moment after it ends. */
@Stable
internal class GestureFeedbackState(private val scope: CoroutineScope) {
    var current by mutableStateOf<GestureFeedback?>(null)
        private set

    private var hideJob: Job? = null

    /** Shows [feedback] until [release]. */
    fun show(feedback: GestureFeedback) {
        hideJob?.cancel()
        current = feedback
    }

    /** Shows [feedback] for a moment - for what is done in one go, like a double tap. */
    fun flash(feedback: GestureFeedback) {
        show(feedback)
        release()
    }

    /** Shows a double tap's seek of [deltaMs], on top of those it follows the same way. */
    fun flashDoubleTapSeek(deltaMs: Long) = flash(GestureFeedback.DoubleTapSeek(accumulatedSeekMs(current, deltaMs)))

    fun release() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(VideoPlayerTokens.FeedbackTimeoutMs)
            current = null
        }
    }
}

/** [deltaMs] added to the double-tap seek showing, while there is one the same way. */
internal fun accumulatedSeekMs(showing: GestureFeedback?, deltaMs: Long): Long {
    val previous = (showing as? GestureFeedback.DoubleTapSeek)?.deltaMs ?: return deltaMs
    return if ((previous < 0) == (deltaMs < 0)) previous + deltaMs else deltaMs
}

/**
 * Every gesture's feedback, each in its own place: a double tap's ripple from the edge it was tapped by, a level's
 * meter on its own side, and whatever else on a pill. Only this composes for it, however fast a drag reports.
 *
 * The last value of each stays drawn as it fades, rather than vanishing with it.
 */
@Composable
internal fun GestureFeedbackLayer(state: GestureFeedbackState, modifier: Modifier = Modifier) {
    val current = state.current
    Box(modifier = modifier.fillMaxSize()) {
        Shown(current as? GestureFeedback.DoubleTapSeek) { seek -> SeekRipple(seek.deltaMs) }
        Shown(current as? GestureFeedback.Brightness, AbsoluteAlignment.CenterLeft, fadeMs = VideoPlayerTokens.MeterFadeMs) { brightness ->
            LevelMeter(PlayerIcons.Brightness, brightness.level)
        }
        Shown(current as? GestureFeedback.Volume, AbsoluteAlignment.CenterRight, fadeMs = VideoPlayerTokens.MeterFadeMs) { volume ->
            LevelMeter(if (volume.level > 0f) PlayerIcons.Volume else PlayerIcons.Muted, volume.level)
        }
        Shown(current as? PillFeedback, Alignment.TopCenter) { FeedbackPill(it) }
    }
}

/** [content] of [value], fading in as it comes and out, as last given, as it goes. */
@Composable
private fun <T : Any> BoxScope.Shown(
    value: T?,
    alignment: Alignment = Alignment.Center,
    fadeMs: Int = VideoPlayerTokens.ScrubFadeMs,
    content: @Composable (T) -> Unit,
) {
    // A plain holder rather than state: what is shown follows [value] in the same composition.
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn(tween(fadeMs)),
        exit = fadeOut(tween(VideoPlayerTokens.ChromeFadeMs)),
        modifier = Modifier.align(alignment),
    ) {
        @Suppress("UNCHECKED_CAST")
        (last[0] as T?)?.let { content(it) }
    }
}

/** The arc of light from the edge a double tap seeks by, swelling with each tap, with how far it has gone in all. */
@Composable
private fun SeekRipple(deltaMs: Long) {
    val isForward = deltaMs >= 0L
    // Each tap swells it from a little smaller: springing, so taps in quick succession run into one another.
    val swell = remember { Animatable(0.9f) }
    LaunchedEffect(deltaMs) {
        swell.snapTo(0.94f)
        swell.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 900f))
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = swell.value; scaleY = swell.value }) {
            val radius = size.height * VideoPlayerTokens.RippleHeightRatio / 2f
            val outside = size.height * VideoPlayerTokens.RippleCentreOutsideRatio
            val centreX = if (isForward) size.width + outside else -outside
            drawCircle(VideoPlayerTokens.RippleColor, radius, Offset(centreX, size.height / 2f))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(if (isForward) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)
                .windowInsetsPadding(videoSafeArea)
                .padding(horizontal = 64.dp),
        ) {
            SeekChevrons(isForward)
            PlayerText(formatSeekSeconds(deltaMs), VideoPlayerType.SeekAmount)
        }
    }
}

/** Three chevrons lighting one after another the way the video went. */
@Composable
private fun SeekChevrons(isForward: Boolean) {
    val sweep = rememberInfiniteTransition(label = "seekChevrons").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 540, easing = LinearEasing), RepeatMode.Restart),
        label = "seekChevronSweep",
    )
    Row(modifier = Modifier.graphicsLayer { scaleX = if (isForward) -1f else 1f }) {
        for (index in 0 until 3) {
            Icon(
                imageVector = PlayerIcons.SeekChevron,
                contentDescription = null,
                tint = VideoPlayerTokens.ContentColor,
                modifier = Modifier
                    .size(18.dp)
                    // Read as it draws: the sweep composes nothing.
                    .graphicsLayer {
                        // Leading from the chevron furthest along, which is the first of the row: it points back.
                        val distance = (sweep.value * 3f - (2 - index) + 3f) % 3f
                        alpha = 0.4f + 0.6f * (1f - distance / 3f)
                    },
            )
        }
    }
}

/**
 * A level on a slim bar, its figure beneath it, on the ground everything over the picture stands on: drawn from the drag
 * directly, with no easing to fall behind the finger. A level past 1 - a boosted volume - keeps the bar full, and fills
 * it again from the bottom in the accent with what is past it.
 */
@Composable
private fun LevelMeter(icon: ImageVector, level: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .windowInsetsPadding(videoSafeArea)
            .padding(horizontal = VideoPlayerTokens.MeterEdgeMargin)
            .background(VideoPlayerTokens.Ground, CircleShape)
            .padding(horizontal = VideoPlayerTokens.GroundPadding, vertical = VideoPlayerTokens.GroundPadding * 2),
    ) {
        Canvas(Modifier.size(VideoPlayerTokens.MeterTrackWidth, VideoPlayerTokens.MeterTrackHeight)) {
            val corner = CornerRadius(size.width / 2f)
            fun fill(fraction: Float, color: Color) {
                val filled = size.height * fraction.coerceIn(0f, 1f)
                drawRoundRect(color, topLeft = Offset(0f, size.height - filled), size = Size(size.width, filled), cornerRadius = corner)
            }
            drawRoundRect(VideoPlayerTokens.TrackColor, cornerRadius = corner)
            fill(level, VideoPlayerTokens.ContentColor)
            fill(level - 1f, VideoPlayerTokens.Accent)
        }
        Icon(imageVector = icon, contentDescription = null, tint = VideoPlayerTokens.ContentColor, modifier = Modifier.size(VideoPlayerTokens.BarIconSize))
        PlayerText((level * 100).roundToInt().toString(), VideoPlayerType.MeterValue)
    }
}

@Composable
private fun FeedbackPill(feedback: PillFeedback) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Kept clear of the system bars and the cutout alike on either side, so it stands in the middle of the screen.
        modifier = Modifier
            .padding(videoControlsPadding(isLandscape = false))
            .padding(top = VideoPlayerTokens.PillTopMargin)
            .background(VideoPlayerTokens.Ground, CircleShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = feedback.icon,
            contentDescription = null,
            tint = VideoPlayerTokens.ContentColor,
            modifier = Modifier.size(18.dp),
        )
        PlayerText(feedback.text(), VideoPlayerType.Pill)
    }
}

private val PillFeedback.icon: ImageVector
    get() = when (this) {
        is GestureFeedback.Speed -> Icons.Rounded.Speed
    }

@Composable
private fun PillFeedback.text(): String = when (this) {
    is GestureFeedback.Speed -> speedLabel(speed)
}
