package com.lhacenmed.sona.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.theme.LocalIsRounded
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.feature.player.swiper.QueueCoverPager
import kotlinx.coroutines.delay
import com.lhacenmed.sona.feature.playback.R as PlaybackR

private const val DoubleTapSeekStepMs = 5_000L
private val ThumbnailCornerRadius = 16.dp

/**
 * The player's artwork: the queue's covers in Auxio's carousel ([QueueCoverPager]), swiped to change
 * track while [swipeToChangeTrack] allows it, and double-tapped on either half to seek - or, while
 * [hideThumbnail], the app's logo in its place. Ported from ArchiveTune's `Thumbnail`.
 */
@Composable
internal fun Thumbnail(
    uiState: PlayerUiState,
    durationMs: Long,
    textBackgroundColor: Color,
    hideThumbnail: Boolean,
    swipeToChangeTrack: Boolean,
    isPlayerExpanded: Boolean,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    uiState.currentTrack ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val latestDurationMs by rememberUpdatedState(durationMs)
    val playingFrom by viewModel.playingFrom.collectAsStateWithLifecycle()

    // Seek on double tap
    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }
    val seekStepSeconds = (DoubleTapSeekStepMs / 1000).toInt()

    // The header's buttons in the header's text colour, so they read over whatever the player is drawn on.
    val headerButtonColors = IconButtonDefaults.iconButtonColors(contentColor = textBackgroundColor)

    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Now Playing header, between the way back down to the mini player and the equalizer -
            // Auxio's playback toolbar. The row ends 4dp from each edge, which puts each button's glyph
            // on the content keyline, as a top bar's do.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 16.dp),
            ) {
                SonaIconButton(
                    onClick = onCollapse,
                    icon = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.player_collapse),
                    colors = headerButtonColors,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.player_now_playing),
                        style = MaterialTheme.typography.titleMedium,
                        color = textBackgroundColor,
                    )
                    // What the queue plays from. Always laid out, so the cover below sits in the same
                    // place whether or not the line has a name to show.
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playingFrom?.label().orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = textBackgroundColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                }
                SonaIconButton(
                    onClick = onOpenEqualizer,
                    icon = Icons.Filled.Equalizer,
                    contentDescription = stringResource(R.string.player_equalizer),
                    colors = headerButtonColors,
                )
            }

            // Thumbnail content
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
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
                val artworkSize = maxWidth - (PlayerHorizontalPadding * 2)
                if (hideThumbnail) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(artworkSize)
                            .clip(roundedShape(ThumbnailCornerRadius))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(
                            painter = painterResource(PlaybackR.drawable.ic_notification),
                            contentDescription = stringResource(R.string.player_hidden_thumbnail),
                            tint = textBackgroundColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(120.dp),
                        )
                    }
                } else {
                    AndroidView(
                        factory = { viewContext -> QueueCoverPager(viewContext, ThumbnailCornerRadius) },
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
            }
        }

        // Seek effect
        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        AnimatedVisibility(
            visible = showSeekEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
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
