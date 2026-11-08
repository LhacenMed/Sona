package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaMarquee
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.core.designsystem.player.PlayerButtonGroup
import com.lhacenmed.sona.core.designsystem.player.PlayerControls
import com.lhacenmed.sona.core.designsystem.player.PlayerTransport
import com.lhacenmed.sona.core.designsystem.player.PlayerTransportColors
import com.lhacenmed.sona.core.designsystem.player.playerButton
import com.lhacenmed.sona.core.designsystem.player.transportIcon
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.feature.playback.labelRes
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType

/** Back, the video's name, then [actions] together - the top of the screen. */
@Composable
internal fun VideoTopBar(
    title: String,
    onBack: () -> Unit,
    actions: List<VideoAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(VideoPlayerTokens.BarButtonSize),
    ) {
        VideoBarButton(VideoAction(PlayerIcons.Back, stringResource(R.string.video_back), onBack))
        // A long name scrolls by rather than being cut, so all of it can be read.
        SonaMarquee(text = title, modifier = Modifier.weight(1f).padding(start = 4.dp)) { PlayerText(title, VideoPlayerType.Title) }
        PlayerButtonGroup {
            actions.forEach { videoButton(it) }
        }
    }
}

/**
 * The video before, play or pause, and the video after - every player's transport, see [PlayerTransport]. Beneath the
 * picture in portrait, play stands on the [VideoPlayerTokens.Accent] as the music player's does, with shuffle and repeat
 * at either end of the same group - on, squared, while on - across the row; over the picture in landscape, the three
 * alone stand on a ground of their own, no scrim lying behind them. [onSkipPrevious] or [onSkipNext] is null where there
 * is no skipping that way. While [isLoading], the loader stands where play and pause's glyph does.
 */
@Composable
internal fun VideoTransport(
    isPlaying: Boolean,
    isLoading: Boolean,
    isLandscape: Boolean,
    isShuffled: Boolean,
    repeatMode: RepeatMode,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: (() -> Unit)?,
    onSkipNext: (() -> Unit)?,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playIcon = if (isPlaying) PlayerIcons.Pause else PlayerIcons.Play
    val playLabel = stringResource(if (isPlaying) R.string.video_pause else R.string.video_play)
    if (isLandscape) {
        PlayerTransport(
            playIcon = playIcon,
            playLabel = playLabel,
            isPaused = !isPlaying,
            onPlayPause = onTogglePlayPause,
            onPrevious = onSkipPrevious,
            onNext = onSkipNext,
            colors = PlayerTransportColors(
                content = VideoPlayerTokens.ContentColor,
                playContainer = VideoPlayerTokens.TransportGround,
                playContent = VideoPlayerTokens.ContentColor,
                skipContainer = VideoPlayerTokens.TransportGround,
            ),
            isLoading = isLoading,
            spacing = VideoPlayerTokens.LandscapeTransportGap,
            modifier = modifier,
        )
        return
    }
    val shuffleLabel = stringResource(R.string.video_shuffle)
    val repeatLabel = stringResource(repeatMode.labelRes)
    val isRepeating = repeatMode != RepeatMode.OFF
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = modifier) {
        PlayerTransport(
            playIcon = playIcon,
            playLabel = playLabel,
            isPaused = !isPlaying,
            onPlayPause = onTogglePlayPause,
            onPrevious = onSkipPrevious,
            onNext = onSkipNext,
            colors = PlayerTransportColors(
                content = VideoPlayerTokens.ContentColor,
                playContainer = VideoPlayerTokens.Accent,
                playContent = VideoPlayerTokens.OnAccent,
            ),
            isLoading = isLoading,
            spacing = PlayerControls.transportGap(maxWidth, hasCorners = true),
            leading = {
                playerButton(PlayerIcons.Shuffle, shuffleLabel, toggleColor(isShuffled), onToggleShuffle, checked = isShuffled)
            },
            trailing = {
                playerButton(repeatMode.transportIcon(), repeatLabel, toggleColor(isRepeating), onCycleRepeatMode, checked = isRepeating)
            },
        )
    }
}

/** A toggle's glyph: whole while on, as quiet as the secondary text while off - as the music player's are. */
private fun toggleColor(isOn: Boolean) = if (isOn) VideoPlayerTokens.ContentColor else VideoPlayerTokens.SecondaryContent
