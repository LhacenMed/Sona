package com.lhacenmed.sona.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lhacenmed.sona.core.designsystem.theme.LocalIsRounded
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.feature.player.swiper.QueueCoverPager
import kotlinx.coroutines.delay

private const val DoubleTapSeekStepMs = 5_000L

/**
 * The player's artwork: the queue's covers in Auxio's carousel ([QueueCoverPager]), swiped to change
 * track while [swipeToChangeTrack] allows it, and double-tapped on either half to seek - or, while
 * [hideThumbnail], the app's logo in its place. Ported from ArchiveTune's `Thumbnail`.
 *
 * Square, as large as the room it is given allows - within the player's keyline, and [PlayerArtworkMinGap] clear of
 * what is above and below it - and centred in that room.
 */
@Composable
internal fun Thumbnail(
    uiState: PlayerUiState,
    durationMs: Long,
    textBackgroundColor: Color,
    hideThumbnail: Boolean,
    swipeToChangeTrack: Boolean,
    isPlayerExpanded: Boolean,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    uiState.currentTrack ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val latestDurationMs by rememberUpdatedState(durationMs)

    // Seek on double tap
    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }
    val seekStepSeconds = (DoubleTapSeekStepMs / 1000).toInt()

    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = modifier) {
        val onDoubleTap = { isBackward: Boolean ->
            view.performContextClick()
            val currentPosition = viewModel.currentPositionMs()
            if (isBackward) {
                viewModel.onSeek((currentPosition - DoubleTapSeekStepMs).coerceAtLeast(0))
                seekDirection = context.getString(R.string.player_seek_backward, seekStepSeconds)
            } else {
                viewModel.onSeek((currentPosition + DoubleTapSeekStepMs).coerceAtMost(latestDurationMs))
                seekDirection = context.getString(R.string.player_seek_forward, seekStepSeconds)
            }
            showSeekEffect = true
        }

        val isRounded = LocalIsRounded.current
        val artworkSize =
            min(maxWidth - PlayerHorizontalPadding * 2, maxHeight - PlayerArtworkMinGap * 2).coerceAtLeast(0.dp)
        if (hideThumbnail) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(artworkSize)
                    .clip(roundedShape(PlayerArtworkCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                    pager.onDoubleTap = onDoubleTap
                    pager.isSwipeEnabled = isPlayerExpanded && swipeToChangeTrack
                    pager.isRounded = isRounded
                    pager.show(uiState.queue, uiState.currentQueueIndex)
                },
                modifier = Modifier.size(artworkSize),
            )
        }

        // Seek effect
        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        AnimatedVisibility(visible = showSeekEffect, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = seekDirection,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .background(Color.Black.copy(alpha = 0.7f), roundedShape(8.dp))
                        .padding(8.dp),
            )
        }
    }
}
