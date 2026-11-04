package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType

/**
 * Back, the video's name and what is known of it, then [actions] - the top of the screen. Under a scrub the name stays
 * to say what is being scrubbed, dimmed; the actions go.
 */
@Composable
internal fun VideoTopBar(
    title: String,
    details: String,
    onBack: () -> Unit,
    actions: List<VideoAction>,
    scrubProgress: State<Float>,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(VideoPlayerTokens.BarButtonSize),
    ) {
        val dimsAway = Modifier.graphicsLayer { alpha = 1f - scrubProgress.value * (1f - VideoPlayerTokens.ScrubChromeAlpha) }
        VideoBarButton(VideoAction(VideoIcons.Back, stringResource(R.string.video_back), onBack), modifier = dimsAway)
        // Its second line's place is kept even with nothing to say, so the name never moves.
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = dimsAway.weight(1f).padding(start = 4.dp)) {
            // A long name scrolls by rather than being cut, so all of it can be read.
            PlayerText(title, VideoPlayerType.Title, modifier = Modifier.basicMarquee())
            PlayerText(details, VideoPlayerType.TitleDetail, color = VideoPlayerTokens.SecondaryContent)
        }
        Row(modifier = Modifier.graphicsLayer { alpha = 1f - scrubProgress.value }) {
            actions.forEach { VideoBarButton(it) }
        }
    }
}

/** Back by [seekSeconds], play or pause, and on by [seekSeconds]: what a thumb reaches for most. */
@Composable
internal fun VideoTransport(
    isPlaying: Boolean,
    seekSeconds: Int,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (seconds: Int) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 48.dp else 44.dp),
        modifier = modifier,
    ) {
        VideoSkipButton(VideoIcons.SkipBack, seekSeconds, stringResource(R.string.video_skip_back, seekSeconds), { onSeekBy(-seekSeconds) })
        VideoBarButton(
            action = VideoAction(
                icon = if (isPlaying) VideoIcons.Pause else VideoIcons.Play,
                label = stringResource(if (isPlaying) R.string.video_pause else R.string.video_play),
                onClick = onTogglePlayPause,
            ),
            size = VideoPlayerTokens.PlayButtonSize,
            iconSize = VideoPlayerTokens.PlayIconSize,
        )
        VideoSkipButton(VideoIcons.SkipForward, seekSeconds, stringResource(R.string.video_skip_forward, seekSeconds), { onSeekBy(seekSeconds) })
    }
}

/** A row of controls. */
@Composable
internal fun VideoActionRow(actions: List<VideoAction>, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        actions.forEach { VideoBarButton(it) }
    }
}
