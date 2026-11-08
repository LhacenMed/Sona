package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.core.designsystem.player.PlayerBarButton
import com.lhacenmed.sona.core.model.Track

/** The size of the artwork beside the lyrics header's title. */
private val LyricsHeaderArtworkSize = 40.dp

/**
 * The bar along the top of the player, beneath the status bar: the way back down to the mini player, what the queue plays
 * from - its name over its kind and how much it holds - then the equalizer. What it plays from,
 * pressed, is opened - [onOpenPlayingFrom], where there is one to open.
 */
@Composable
internal fun NowPlayingHeader(
    playingFrom: PlayingFrom?,
    queue: List<QueueTrack>,
    contentColor: Color,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenPlayingFrom: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isAllVideos = remember(queue) { queue.isNotEmpty() && queue.all { it.track.isVideo } }
    val count =
        pluralStringResource(if (isAllVideos) R.plurals.player_video_count else R.plurals.player_song_count, queue.size, queue.size)
    val kind = playingFrom?.kindLabel()
    PlayerHeaderBar(modifier) {
        PlayerBarButton(PlayerIcons.ChevronDown, stringResource(R.string.player_collapse), contentColor, onCollapse)
        PlayerHeaderTitle(
            title = playingFrom?.title() ?: stringResource(R.string.player_now_playing),
            subtitle = if (kind != null) stringResource(R.string.player_playing_from_detail, kind, count) else count,
            color = contentColor,
            onClick = onOpenPlayingFrom,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        PlayerBarButton(PlayerIcons.Equalizer, stringResource(R.string.player_equalizer), contentColor, onOpenEqualizer)
    }
}

/**
 * The bar along the top of the lyrics sheet, where the player's own is: the way back to the player, the track playing -
 * its artwork beside its title and artist - and the lyrics' own menu.
 */
@Composable
internal fun LyricsHeader(
    track: Track,
    contentColor: Color,
    onClose: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerHeaderBar(modifier) {
        PlayerBarButton(PlayerIcons.ChevronDown, stringResource(R.string.player_close), contentColor, onClose)
        SonaCoverArt(
            coverArtUri = track.coverArtUri,
            contentDescription = null,
            size = LyricsHeaderArtworkSize,
            modifier = Modifier.padding(start = 4.dp),
        )
        PlayerHeaderTitle(
            title = track.title,
            subtitle = track.artist,
            color = contentColor,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        PlayerBarButton(PlayerIcons.More, stringResource(R.string.player_more_options), contentColor, onMenuClick)
    }
}

/** A row of the player's header, its buttons at either end on the player's keyline. */
@Composable
private fun PlayerHeaderBar(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = PlayerHorizontalPadding)
                .height(PlayerBarButtonSize),
        content = content,
    )
}

/** The header's two lines: its title, and the quieter line beneath it - pressed as one, where there is an [onClick]. */
@Composable
private fun PlayerHeaderTitle(
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier =
            modifier
                .clickable(
                    enabled = onClick != null,
                    role = Role.Button,
                    interactionSource = null,
                    indication = null,
                ) { onClick?.invoke() },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            color = color.copy(alpha = PlayerSecondaryAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
