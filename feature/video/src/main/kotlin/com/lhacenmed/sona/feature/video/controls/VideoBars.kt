package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.formatVideoTime
import kotlin.math.abs
import kotlinx.coroutines.delay

/** How close the player's position has to come to where a drag let go for the seek bar to follow it again. */
private const val SeekSettleToleranceMs = 1_000L

/** How long the seek bar waits for the player to catch up before following it wherever it is. */
private const val SeekSettleTimeoutMs = 1_500L

/** Back, the video's name, then [actions] - the top of the screen. */
@Composable
internal fun VideoTopBar(
    title: String,
    onBack: () -> Unit,
    actions: List<VideoAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().height(VideoPlayerTokens.BarHeight),
    ) {
        VideoBarButton(VideoAction(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.video_back), onBack))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = VideoPlayerTokens.ContentColor,
            maxLines = 1,
            // A long name scrolls by rather than being cut, so all of it can be read.
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp).basicMarquee(),
        )
        actions.forEach { VideoBarButton(it) }
    }
}

/**
 * The position, the seek bar and the length. Dragging it seeks as it goes - the player scrubbing meanwhile, so
 * each frame shows at once - and the position shown is where the thumb is, until the drag lets go and then until
 * the player has caught up with it, so the thumb never jumps back for a moment. Letting go seeks nothing more: the
 * last seek of the drag was already to where it let go.
 *
 * While not [enabled] - the controls locked - it still shows how far the video is, and cannot be moved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var draggedMs by remember { mutableStateOf<Long?>(null) }
    var settlingMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(settlingMs) {
        if (settlingMs == null) return@LaunchedEffect
        delay(SeekSettleTimeoutMs)
        settlingMs = null
    }
    val shownMs = draggedMs ?: settlingMs?.takeIf { abs(positionMs - it) > SeekSettleToleranceMs } ?: positionMs
    val canSeek = enabled && durationMs > 0L
    val primary = MaterialTheme.colorScheme.primary
    val inactiveTrack = Color.White.copy(alpha = 0.3f)
    // Locked, it is a progress bar: the same colours, and no thumb.
    val colors = SliderDefaults.colors(
        thumbColor = VideoPlayerTokens.ContentColor,
        activeTrackColor = primary,
        inactiveTrackColor = inactiveTrack,
        disabledThumbColor = Color.Transparent,
        disabledActiveTrackColor = primary,
        disabledInactiveTrackColor = inactiveTrack,
    )
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = VideoPlayerTokens.EdgePadding),
    ) {
        TimeText(shownMs)
        Slider(
            value = if (durationMs > 0L) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            onValueChange = { fraction ->
                val targetMs = (fraction * durationMs).toLong()
                if (draggedMs == null) onScrubbingChange(true)
                draggedMs = targetMs
                onSeek(targetMs)
            },
            onValueChangeFinished = {
                settlingMs = draggedMs
                draggedMs = null
                onScrubbingChange(false)
            },
            enabled = canSeek,
            colors = colors,
            interactionSource = interactionSource,
            // A round dot, as a video player's is, rather than Material's tall handle.
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    enabled = canSeek,
                    thumbSize = DpSize(14.dp, 14.dp),
                    modifier = Modifier.clip(CircleShape),
                )
            },
            modifier = Modifier.weight(1f),
        )
        TimeText(durationMs)
    }
}

@Composable
private fun TimeText(timeMs: Long) {
    Text(
        text = formatVideoTime(timeMs),
        // Figures of one width, so the time does not jitter as it counts.
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = VideoPlayerTokens.ContentColor,
    )
}

/**
 * Play or pause, previous and next, then - at the far end - the speed and [trailing] controls: the bottom of
 * the screen, beneath the seek bar.
 */
@Composable
internal fun VideoTransportBar(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    speedLabel: String,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSpeedClick: () -> Unit,
    trailing: List<VideoAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(VideoPlayerTokens.TransportBarHeight)
            .padding(horizontal = VideoPlayerTokens.EdgePadding - 4.dp),
    ) {
        PlayPauseButton(isPlaying, onTogglePlayPause)
        Spacer(Modifier.size(8.dp))
        VideoBarButton(
            VideoAction(Icons.Rounded.SkipPrevious, stringResource(R.string.video_previous), onSkipPrevious.takeIf { canSkipPrevious }),
        )
        VideoBarButton(
            VideoAction(Icons.Rounded.SkipNext, stringResource(R.string.video_next), onSkipNext.takeIf { canSkipNext }),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = speedLabel,
            style = MaterialTheme.typography.titleMedium,
            color = VideoPlayerTokens.ContentColor,
            overflow = TextOverflow.Clip,
            maxLines = 1,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSpeedClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        trailing.forEach { VideoBarButton(it) }
    }
}

/** The ringed play and pause button the transport bar starts with. */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(VideoPlayerTokens.PlayButtonSize)
            .clip(CircleShape)
            .border(2.dp, VideoPlayerTokens.ContentColor, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(if (isPlaying) R.string.video_pause else R.string.video_play),
            tint = VideoPlayerTokens.ContentColor,
            modifier = Modifier.size(VideoPlayerTokens.PlayIconSize),
        )
    }
}
