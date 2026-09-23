@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player.style

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.player.PlayerHorizontalPadding
import com.lhacenmed.sona.feature.player.PlayerSlider
import com.lhacenmed.sona.feature.player.PlayerTimeLabel
import com.lhacenmed.sona.feature.player.PlayerTitleActions
import com.lhacenmed.sona.feature.player.PlayerTitleSection
import com.lhacenmed.sona.feature.player.PlayerUiState
import com.lhacenmed.sona.feature.player.PlayerViewModel
import com.lhacenmed.sona.feature.player.R
import com.lhacenmed.sona.feature.player.Thumbnail
import com.lhacenmed.sona.feature.player.favoriteIconRes
import com.lhacenmed.sona.feature.player.playPauseIconRes
import com.lhacenmed.sona.feature.player.transportIconRes
import com.lhacenmed.sona.feature.playback.R as PlaybackR

/** The Default player's colours: the app theme's own. */
internal object DefaultPlayerColors {
    val sheet: Color
        @Composable get() = MaterialTheme.colorScheme.surface

    val content: Color
        @Composable get() = MaterialTheme.colorScheme.onBackground
}

/**
 * The Default player: the artwork above its title, seek bar and transport - side by side in landscape.
 * ArchiveTune's Cinematic player.
 */
@Composable
internal fun DefaultPlayer(
    track: Track,
    uiState: PlayerUiState,
    sliderStyle: PlayerSliderStyle,
    isLoading: Boolean,
    isPlayerExpanded: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    queueBarHeight: Dp,
    titleActions: PlayerTitleActions,
    onMenuClick: () -> Unit,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val contentColor = DefaultPlayerColors.content
    val controls: @Composable () -> Unit = {
        DefaultPlayerControls(
            track = track,
            sliderStyle = sliderStyle,
            playback = uiState.playback,
            isLoading = isLoading,
            isFavorite = uiState.isCurrentTrackFavorite,
            contentColor = contentColor,
            onContentColor = DefaultPlayerColors.sheet,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            titleActions = titleActions,
            onMenuClick = onMenuClick,
            viewModel = viewModel,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
        )
    }

    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Row(
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = queueBarHeight + 48.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f),
            ) {
                val thumbnailSize = (LocalConfiguration.current.screenWidthDp * 0.4).dp
                Thumbnail(
                    uiState = uiState,
                    durationMs = duration,
                    textBackgroundColor = contentColor,
                    isPlayerExpanded = isPlayerExpanded,
                    onCollapse = onCollapse,
                    onOpenEqualizer = onOpenEqualizer,
                    viewModel = viewModel,
                    modifier = Modifier.size(thumbnailSize),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
            ) {
                Spacer(Modifier.weight(1f))

                controls()

                Spacer(Modifier.weight(1f))
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = queueBarHeight),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f),
            ) {
                Thumbnail(
                    uiState = uiState,
                    durationMs = duration,
                    textBackgroundColor = contentColor,
                    isPlayerExpanded = isPlayerExpanded,
                    onCollapse = onCollapse,
                    onOpenEqualizer = onOpenEqualizer,
                    viewModel = viewModel,
                )
            }

            controls()

            Spacer(Modifier.height(30.dp))
        }
    }
}

/** The title and its actions, seek bar, times and transport. */
@Composable
private fun DefaultPlayerControls(
    track: Track,
    sliderStyle: PlayerSliderStyle,
    playback: PlaybackUiState,
    isLoading: Boolean,
    isFavorite: Boolean,
    contentColor: Color,
    onContentColor: Color,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    titleActions: PlayerTitleActions,
    onMenuClick: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PlayerTitleSection(
                track = track,
                textBackgroundColor = contentColor,
                titleActions = titleActions,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        DefaultTrackActions(
            contentColor = contentColor,
            isFavorite = isFavorite,
            onToggleFavorite = viewModel::onToggleFavorite,
            onMenuClick = onMenuClick,
        )
    }

    Spacer(Modifier.height(12.dp))

    PlayerSlider(
        sliderStyle = sliderStyle,
        sliderPosition = sliderPosition,
        position = position,
        duration = duration,
        isPlaying = playback.isPlaying,
        textButtonColor = contentColor,
        onValueChange = onSliderValueChange,
        onValueChangeFinished = onSliderValueChangeFinished,
    )

    Spacer(Modifier.height(4.dp))

    PlayerTimeLabel(
        sliderPosition = sliderPosition,
        position = position,
        duration = duration,
        textBackgroundColor = contentColor,
    )

    Spacer(Modifier.height(12.dp))

    DefaultTransportControls(
        playback = playback,
        isLoading = isLoading,
        contentColor = contentColor,
        onContentColor = onContentColor,
        viewModel = viewModel,
    )
}

/**
 * Favourite and more, as glass cards beside the title. Sharing is in the track's options sheet, which
 * more opens, so the title keeps the room a third card would take.
 */
@Composable
private fun DefaultTrackActions(
    contentColor: Color,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onToggleFavorite,
            shape = RoundedCornerShape(14.dp),
            color =
                if (isFavorite) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                } else {
                    contentColor.copy(alpha = 0.12f)
                },
            modifier =
                Modifier
                    .height(44.dp)
                    .width(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(favoriteIconRes(isFavorite)),
                    contentDescription = null,
                    // The theme's own colour, as the queue's favourite button marks a favourite.
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Surface(
            onClick = onMenuClick,
            shape = RoundedCornerShape(14.dp),
            color = contentColor.copy(alpha = 0.12f),
            modifier =
                Modifier
                    .height(44.dp)
                    .width(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Shuffle, previous, play/pause, next and repeat, scaled down together to fit a narrow screen. The
 * play/pause button squares off while playing.
 */
@Composable
private fun DefaultTransportControls(
    playback: PlaybackUiState,
    isLoading: Boolean,
    contentColor: Color,
    onContentColor: Color,
    viewModel: PlayerViewModel,
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val shuffleEnabled = playback.shuffleEnabled
    val repeatMode = playback.repeatMode
    val canSkipPrevious = playback.canSkipPrevious
    val canSkipNext = playback.canSkipNext

    val onPlayPause = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.onTogglePlayPause()
    }
    val onPrevious = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.onSkipPrevious()
    }
    val onNext = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.onSkipNext()
    }
    val onShuffle = {
        view.performContextClick()
        viewModel.onToggleShuffle()
    }
    val onRepeat = {
        view.performContextClick()
        viewModel.onCycleRepeatMode()
    }

    val playPauseCorner by animateDpAsState(
        targetValue = if (playback.isPlaying) 28.dp else 44.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "playPauseCorner",
    )

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding),
    ) {
        val baseLarge = 56.dp
        val baseSmall = 46.dp
        val baseGap = 12.dp
        val baseLargeIcon = 28.dp
        val baseSmallIcon = 22.dp
        val baseLargeRadius = 18.dp
        val baseSmallRadius = 16.dp
        val centerSize = 88.dp
        val centerPadding = 40.dp
        val sideTotal = (maxWidth - centerSize - centerPadding) / 2f
        val scale =
            ((sideTotal - baseGap) / (baseLarge + baseSmall)).coerceAtMost(1f).coerceAtLeast(0.6f)
        val large = baseLarge * scale
        val small = baseSmall * scale
        val gap = baseGap * scale
        val largeIcon = baseLargeIcon * scale
        val smallIcon = baseSmallIcon * scale
        val largeRadius = baseLargeRadius * scale
        val smallRadius = baseSmallRadius * scale

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onShuffle,
                    shape = RoundedCornerShape(smallRadius),
                    color = contentColor.copy(alpha = if (shuffleEnabled) 0.2f else 0.08f),
                    modifier = Modifier.size(small),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(PlaybackR.drawable.shuffle),
                            contentDescription = null,
                            tint = contentColor.copy(alpha = if (shuffleEnabled) 1f else 0.6f),
                            modifier = Modifier.size(smallIcon),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(gap))

                Surface(
                    onClick = onPrevious,
                    enabled = canSkipPrevious,
                    shape = RoundedCornerShape(largeRadius),
                    color = contentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(large),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = null,
                            tint = contentColor.copy(alpha = if (canSkipPrevious) 1f else 0.4f),
                            modifier = Modifier.size(largeIcon),
                        )
                    }
                }
            }

            Surface(
                onClick = onPlayPause,
                shape = RoundedCornerShape(playPauseCorner),
                color = contentColor,
                modifier =
                    Modifier
                        .padding(horizontal = 20.dp)
                        .size(88.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = onContentColor,
                        )
                    } else {
                        Icon(
                            painter = painterResource(playback.playPauseIconRes()),
                            contentDescription = null,
                            tint = onContentColor,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onNext,
                    enabled = canSkipNext,
                    shape = RoundedCornerShape(largeRadius),
                    color = contentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(large),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = null,
                            tint = contentColor.copy(alpha = if (canSkipNext) 1f else 0.4f),
                            modifier = Modifier.size(largeIcon),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(gap))

                Surface(
                    onClick = onRepeat,
                    shape = RoundedCornerShape(smallRadius),
                    color = contentColor.copy(alpha = if (repeatMode != RepeatMode.OFF) 0.2f else 0.08f),
                    modifier = Modifier.size(small),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(repeatMode.transportIconRes()),
                            contentDescription = null,
                            tint = contentColor.copy(alpha = if (repeatMode == RepeatMode.OFF) 0.6f else 1f),
                            modifier = Modifier.size(smallIcon),
                        )
                    }
                }
            }
        }
    }
}
