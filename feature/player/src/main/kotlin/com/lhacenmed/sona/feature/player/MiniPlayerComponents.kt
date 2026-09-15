@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How readily a sideways swipe changes track - ArchiveTune's default `SwipeSensitivity`. */
private const val SwipeSensitivity = 0.73f

private val MiniPlayerTransportButtonSpacing = 4.dp

@Immutable
internal data class MiniPlayerContentColors(
    val title: Color,
    val secondary: Color,
    val progress: Color,
    val progressTrack: Color,
    val artworkContainer: Color,
    val artworkBorder: Color,
    val primaryButtonContainer: Color,
    val primaryButtonIcon: Color,
    val secondaryButtonContainer: Color,
    val buttonIcon: Color,
    val disabledButtonIcon: Color,
)

@Composable
internal fun SwipeableMiniPlayerBox(
    hasPreviousTrack: Boolean,
    hasNextTrack: Boolean,
    onSwipeToPrevious: () -> Unit,
    onSwipeToNext: () -> Unit,
    layoutDirection: LayoutDirection,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
    content: @Composable (Float) -> Unit,
) {
    val offsetXAnimatable = remember { Animatable(0f) }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    val view = LocalView.current
    // The gesture handler outlives compositions, so it reads these as they are when it runs.
    val latestHasPreviousTrack by rememberUpdatedState(hasPreviousTrack)
    val latestHasNextTrack by rememberUpdatedState(hasNextTrack)
    val latestOnSwipeToPrevious by rememberUpdatedState(onSwipeToPrevious)
    val latestOnSwipeToNext by rememberUpdatedState(onSwipeToNext)

    val animationSpec =
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        )

    val autoSwipeThreshold = (600 / (1f + exp(-(-11.44748 * SwipeSensitivity + 9.04945)))).roundToInt()

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerHeight)
                    .padding(horizontal = MiniPlayerHorizontalPadding)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragStartTime = System.currentTimeMillis()
                                totalDragDistance = 0f
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    offsetXAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = animationSpec,
                                    )
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val adjustedDragAmount =
                                    if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                                val allowLeft = adjustedDragAmount < 0 && latestHasNextTrack
                                val allowRight = adjustedDragAmount > 0 && latestHasPreviousTrack
                                if (allowLeft || allowRight) {
                                    totalDragDistance += abs(adjustedDragAmount)
                                    coroutineScope.launch {
                                        offsetXAnimatable.snapTo(offsetXAnimatable.value + adjustedDragAmount)
                                    }
                                }
                            },
                            onDragEnd = {
                                val dragDuration = System.currentTimeMillis() - dragStartTime
                                val velocity = if (dragDuration > 0) totalDragDistance / dragDuration else 0f
                                val currentOffset = offsetXAnimatable.value

                                val minDistanceThreshold = 50f
                                val velocityThreshold = (SwipeSensitivity * -8.25f) + 8.5f

                                val shouldChangeSong =
                                    (
                                        abs(currentOffset) > minDistanceThreshold &&
                                            velocity > velocityThreshold
                                    ) || (abs(currentOffset) > autoSwipeThreshold)

                                if (shouldChangeSong) {
                                    val isRightSwipe = currentOffset > 0

                                    if (isRightSwipe && latestHasPreviousTrack) {
                                        view.performContextClick()
                                        latestOnSwipeToPrevious()
                                    } else if (!isRightSwipe && latestHasNextTrack) {
                                        view.performContextClick()
                                        latestOnSwipeToNext()
                                    }
                                }

                                coroutineScope.launch {
                                    offsetXAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = animationSpec,
                                    )
                                }
                            },
                        )
                    },
        ) {
            content(offsetXAnimatable.value)

            if (offsetXAnimatable.value.absoluteValue > 50f) {
                Box(
                    modifier =
                        Modifier
                            .align(if (offsetXAnimatable.value > 0) Alignment.CenterStart else Alignment.CenterEnd)
                            .padding(horizontal = 16.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (offsetXAnimatable.value > 0) R.drawable.skip_previous else R.drawable.skip_next,
                            ),
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = (offsetXAnimatable.value.absoluteValue / autoSwipeThreshold).coerceIn(0f, 1f),
                            ),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.MiniPlayerInfo(
    track: Track,
    colors: MiniPlayerContentColors,
) {
    Column(
        modifier =
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = track.title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "title",
        ) { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }

        AnimatedContent(
            targetState = track.artist,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "artist",
        ) { artist ->
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
}

@Composable
private fun MiniPlayerArtwork(
    coverArtUri: String?,
    progress: () -> Float,
    isLoading: Boolean,
    colors: MiniPlayerContentColors,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(52.dp),
    ) {
        if (isLoading) {
            CircularWavyProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = colors.progress,
                trackColor = colors.progressTrack,
            )
        } else {
            CircularWavyProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
                color = colors.progress,
                trackColor = colors.progressTrack,
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.artworkContainer)
                    .border(
                        width = 1.dp,
                        color = colors.artworkBorder,
                        shape = CircleShape,
                    ),
        ) {
            SonaCoverImage(
                coverArtUri = coverArtUri,
                contentDescription = null,
                cornerRadius = 0.dp,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MiniPlayerTransportButton(
    @DrawableRes iconResId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    colors: MiniPlayerContentColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
) {
    val view = LocalView.current

    val buttonColors =
        IconButtonDefaults.iconButtonColors(
            containerColor = if (isPrimary) colors.primaryButtonContainer else colors.secondaryButtonContainer,
            contentColor = if (isPrimary) colors.primaryButtonIcon else colors.buttonIcon,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.disabledButtonIcon,
        )
    val handleClick = {
        view.performContextClick()
        onClick()
    }
    val content: @Composable () -> Unit = {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = contentDescription,
            modifier = Modifier.size(if (isPrimary) 24.dp else 20.dp),
        )
    }

    if (isPrimary) {
        FilledIconButton(
            onClick = handleClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier.size(48.dp),
            enabled = enabled,
            colors = buttonColors,
            content = content,
        )
    } else {
        IconButton(
            onClick = handleClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier.size(48.dp),
            enabled = enabled,
            colors = buttonColors,
            content = content,
        )
    }
}

@Composable
private fun MiniPlayerTransportControls(
    playback: PlaybackUiState,
    viewModel: PlayerViewModel,
    colors: MiniPlayerContentColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MiniPlayerTransportButtonSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPlayerTransportButton(
            iconResId = R.drawable.skip_previous,
            contentDescription = stringResource(R.string.player_previous),
            onClick = viewModel::onSkipPrevious,
            enabled = playback.canSkipPrevious,
            colors = colors,
        )

        MiniPlayerTransportButton(
            iconResId = playback.playPauseIconRes(),
            contentDescription =
                stringResource(
                    if (playback.hasEnded || !playback.isPlaying) R.string.player_play else R.string.player_pause,
                ),
            onClick = viewModel::onTogglePlayPause,
            isPrimary = true,
            colors = colors,
        )

        MiniPlayerTransportButton(
            iconResId = R.drawable.skip_next,
            contentDescription = stringResource(R.string.player_next),
            onClick = viewModel::onSkipNext,
            enabled = playback.canSkipNext,
            colors = colors,
        )
    }
}

@Composable
internal fun MiniPlayerContent(
    track: Track?,
    playback: PlaybackUiState,
    position: Long,
    duration: Long,
    viewModel: PlayerViewModel,
    colors: MiniPlayerContentColors,
) {
    val progressProvider =
        remember(position, duration) {
            { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f }
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        MiniPlayerArtwork(
            coverArtUri = track?.coverArtUri,
            progress = progressProvider,
            isLoading = playback.isBuffering,
            colors = colors,
        )

        if (track != null) {
            MiniPlayerInfo(
                track = track,
                colors = colors,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        MiniPlayerTransportControls(
            playback = playback,
            viewModel = viewModel,
            colors = colors,
        )
    }
}
