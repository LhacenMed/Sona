package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType

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
        modifier = modifier
            .fillMaxWidth()
            .height(VideoPlayerTokens.BarButtonSize),
    ) {
        VideoBarButton(VideoAction(PlayerIcons.Back, stringResource(R.string.video_back), onBack))
        // A long name scrolls by rather than being cut, so all of it can be read.
        PlayerText(title, VideoPlayerType.Title, modifier = Modifier.weight(1f).padding(start = 4.dp).basicMarquee())
        VideoButtonGroup {
            actions.forEach { videoButton(it) }
        }
    }
}

/**
 * The video before, play or pause, and the video after: what a thumb reaches for most. [onSkipPrevious] or [onSkipNext]
 * is null where there is no skipping that way. While [isLoading], the loader stands where play and pause's icon does;
 * the button under it still answers.
 */
@Composable
internal fun VideoTransport(
    isPlaying: Boolean,
    isLoading: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: (() -> Unit)?,
    onSkipNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val previous = VideoAction(PlayerIcons.SkipPrevious, stringResource(R.string.video_previous), onSkipPrevious)
    val playPause = VideoAction(
        icon = if (isPlaying) PlayerIcons.Pause else PlayerIcons.Play,
        label = stringResource(if (isPlaying) R.string.video_pause else R.string.video_play),
        onClick = onTogglePlayPause,
        tint = if (isLoading) Color.Transparent else VideoPlayerTokens.ContentColor,
    )
    val next = VideoAction(PlayerIcons.SkipNext, stringResource(R.string.video_next), onSkipNext)
    VideoButtonGroup(modifier = modifier, spacing = VideoPlayerTokens.TransportGap) {
        // On a ground of their own: in the middle of the picture, no scrim lies behind them.
        videoButton(previous, VideoPlayerTokens.SkipButtonSize, VideoPlayerTokens.SkipIconSize, VideoPlayerTokens.TransportGround)
        videoButton(playPause, VideoPlayerTokens.PlayButtonSize, VideoPlayerTokens.PlayIconSize, VideoPlayerTokens.TransportGround)
        videoButton(next, VideoPlayerTokens.SkipButtonSize, VideoPlayerTokens.SkipIconSize, VideoPlayerTokens.TransportGround)
    }
}
