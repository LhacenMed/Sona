@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
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
    chapter: String?,
    onOpenChapters: () -> Unit,
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
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            // A long name scrolls by rather than being cut, so all of it can be read.
            SonaMarquee(text = title) { PlayerText(title, VideoPlayerType.Title) }
            chapter?.let { ChapterButton(it, onOpenChapters) }
        }
        PlayerButtonGroup {
            actions.forEach { videoButton(it) }
        }
    }
}

/** The chapter playing, under the title - pressed, the video's chapters open. As tall as its line: the bar is the touch target. */
@Composable
private fun ChapterButton(chapter: String, onClick: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        TextButton(
            onClick = onClick,
            shapes = buttonPressShapes(),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = VideoPlayerTokens.SecondaryContent),
            modifier = Modifier.height(16.dp),
        ) {
            PlayerText(chapter, VideoPlayerType.Chapter, color = VideoPlayerTokens.SecondaryContent, modifier = Modifier.weight(1f, fill = false))
            Icon(PlayerIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp))
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
                playContainer = VideoPlayerTokens.Ground,
                playContent = VideoPlayerTokens.ContentColor,
                skipContainer = VideoPlayerTokens.Ground,
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
