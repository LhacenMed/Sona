package com.lhacenmed.sona.feature.video.gesture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenLockLandscape
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.datastore.VideoAspect
import com.lhacenmed.sona.core.datastore.VideoOrientation
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.formatVideoTime
import com.lhacenmed.sona.feature.video.label
import com.lhacenmed.sona.feature.video.speedLabel
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a gesture, or a control with nothing else to show for it, tells the user it did - shown mid-screen. */
internal sealed interface GestureFeedback {
    /** Seeking to [targetMs], [deltaMs] from where the seek began. */
    data class Seek(val targetMs: Long, val deltaMs: Long, val durationMs: Long) : GestureFeedback

    data class Brightness(val level: Float) : GestureFeedback

    data class Volume(val level: Float) : GestureFeedback

    data class Speed(val speed: Float) : GestureFeedback

    /** Playing now, or paused - what a double tap in the middle turned it to. */
    data class PlayPause(val isPlaying: Boolean) : GestureFeedback

    data class Aspect(val aspect: VideoAspect) : GestureFeedback

    data class Orientation(val orientation: VideoOrientation) : GestureFeedback
}

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

    fun release() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(VideoPlayerTokens.FeedbackTimeoutMs)
            current = null
        }
    }
}

/**
 * The last gesture's feedback, shown while it is under way and briefly after: the last value given stays drawn
 * as it fades, rather than vanishing with it.
 */
@Composable
internal fun GestureFeedbackBadge(feedback: GestureFeedback?, modifier: Modifier = Modifier) {
    // A plain holder rather than state: what is shown follows [feedback] in the same composition.
    val last = remember { arrayOfNulls<GestureFeedback>(1) }
    val shown = feedback ?: last[0]
    last[0] = shown
    AnimatedVisibility(
        visible = feedback != null,
        enter = VideoPlayerTokens.ControlsEnter,
        exit = VideoPlayerTokens.ControlsExit,
        modifier = modifier,
    ) {
        shown?.let { FeedbackContent(it) }
    }
}

@Composable
private fun FeedbackContent(feedback: GestureFeedback) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .background(VideoPlayerTokens.FeedbackScrim, CircleShape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = feedback.icon,
            contentDescription = null,
            tint = VideoPlayerTokens.ContentColor,
            modifier = Modifier.size(VideoPlayerTokens.ControlIconSize),
        )
        when (feedback) {
            is GestureFeedback.Brightness -> LevelBar(feedback.level)
            is GestureFeedback.Volume -> LevelBar(feedback.level)
            // Its icon says it all.
            is GestureFeedback.PlayPause -> Unit
            else -> Text(
                text = feedback.text(),
                style = MaterialTheme.typography.titleMedium,
                color = VideoPlayerTokens.ContentColor,
            )
        }
    }
}

@Composable
private fun LevelBar(level: Float) {
    LinearProgressIndicator(
        progress = { level },
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.White.copy(alpha = 0.3f),
        modifier = Modifier.width(120.dp),
    )
    Text(
        text = stringResource(R.string.video_percent, (level * 100).roundToInt()),
        style = MaterialTheme.typography.titleMedium,
        color = VideoPlayerTokens.ContentColor,
    )
}

private val GestureFeedback.icon: ImageVector
    get() = when (this) {
        is GestureFeedback.Seek -> if (deltaMs < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward
        is GestureFeedback.Brightness -> Icons.Rounded.BrightnessMedium
        is GestureFeedback.Volume -> Icons.AutoMirrored.Rounded.VolumeUp
        is GestureFeedback.Speed -> Icons.Rounded.Speed
        is GestureFeedback.PlayPause -> if (isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
        is GestureFeedback.Aspect -> Icons.Rounded.AspectRatio
        is GestureFeedback.Orientation -> orientation.icon
    }

/** The rotation button's icon for each way of standing - and its feedback's. */
internal val VideoOrientation.icon: ImageVector
    get() = when (this) {
        VideoOrientation.AUTO -> Icons.Rounded.ScreenRotation
        VideoOrientation.PORTRAIT -> Icons.Rounded.ScreenLockPortrait
        VideoOrientation.LANDSCAPE -> Icons.Rounded.ScreenLockLandscape
    }

@Composable
internal fun VideoOrientation.label(): String =
    stringResource(
        when (this) {
            VideoOrientation.AUTO -> R.string.video_rotation_auto
            VideoOrientation.PORTRAIT -> R.string.video_rotation_portrait
            VideoOrientation.LANDSCAPE -> R.string.video_rotation_landscape
        },
    )

@Composable
private fun GestureFeedback.text(): String = when (this) {
    is GestureFeedback.Seek -> {
        val sign = if (deltaMs < 0) "-" else "+"
        "$sign${formatVideoTime(deltaMs.absoluteValue)}  ${formatVideoTime(targetMs)} / ${formatVideoTime(durationMs)}"
    }
    is GestureFeedback.Speed -> speedLabel(speed)
    is GestureFeedback.Aspect -> aspect.label()
    is GestureFeedback.Orientation -> orientation.label()
    is GestureFeedback.Brightness, is GestureFeedback.Volume, is GestureFeedback.PlayPause -> ""
}
