package com.lhacenmed.sona.feature.player

import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import com.lhacenmed.sona.core.designsystem.theme.LocalIsRounded
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.feature.player.swiper.QueueCoverPager

/**
 * The player's artwork: the queue's covers in Auxio's carousel ([QueueCoverPager]), swiped to change
 * track while [swipeToChangeTrack] allows it, and tapped for [onTap] - the lyrics - or, while [hideThumbnail], the
 * app's logo in its place, tapped the same. The artwork itself is what answers a tap, so no ripple is laid over it.
 * Ported from ArchiveTune's `Thumbnail`.
 *
 * Square, as large as the room it is given allows - within the player's keyline, and [PlayerArtworkMinGap] clear of
 * what is above and below it - and centred in that room.
 */
@Composable
internal fun Thumbnail(
    uiState: PlayerUiState,
    textBackgroundColor: Color,
    hideThumbnail: Boolean,
    swipeToChangeTrack: Boolean,
    isPlayerExpanded: Boolean,
    onTap: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    uiState.currentTrack ?: return

    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = modifier) {
        val isRounded = LocalIsRounded.current
        val artworkSize =
            min(maxWidth - PlayerHorizontalPadding * 2, maxHeight - PlayerArtworkMinGap * 2).coerceAtLeast(0.dp)
        if (hideThumbnail) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(artworkSize)
                    .clip(roundedShape(PlayerArtworkCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(interactionSource = null, indication = null, onClick = onTap),
            ) {
                Icon(
                    imageVector = SonaIcons.CoverPlaceholder,
                    contentDescription = stringResource(R.string.player_hidden_thumbnail),
                    tint = textBackgroundColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(120.dp),
                )
            }
        } else {
            AndroidView(
                factory = { viewContext -> QueueCoverPager(viewContext, PlayerArtworkCornerRadius) },
                update = { pager ->
                    pager.onSwipeToTrack = viewModel::onPlayQueueItem
                    pager.onTap = onTap
                    pager.isSwipeEnabled = isPlayerExpanded && swipeToChangeTrack
                    pager.isRounded = isRounded
                    pager.show(uiState.queue, uiState.currentQueueIndex)
                },
                modifier = Modifier.size(artworkSize),
            )
        }
    }
}
