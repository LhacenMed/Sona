@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.designsystem.motion.RubberBandSettleDurationMillis
import com.lhacenmed.sona.core.designsystem.motion.RubberBandSettleEasing
import com.lhacenmed.sona.core.designsystem.motion.SwipeArmFraction
import com.lhacenmed.sona.core.designsystem.motion.SwipeStretchMaxFraction
import com.lhacenmed.sona.core.designsystem.motion.rubberBandOffset
import com.lhacenmed.sona.core.designsystem.motion.rubberBandPull
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sign
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

    val view = LocalView.current
    // The gesture handler outlives compositions, so it reads these as they are when it runs.
    val latestHasPreviousTrack by rememberUpdatedState(hasPreviousTrack)
    val latestHasNextTrack by rememberUpdatedState(hasNextTrack)
    val latestOnSwipeToPrevious by rememberUpdatedState(onSwipeToPrevious)
    val latestOnSwipeToNext by rememberUpdatedState(onSwipeToNext)

    // The player falling back to rest - Khatmah's page falling back over an unfinished wall.
    val animationSpec = tween<Float>(durationMillis = RubberBandSettleDurationMillis, easing = RubberBandSettleEasing)

    // Read by the gesture handler as it runs, so it always measures against the player's current width.
    var playerWidth by remember { mutableIntStateOf(0) }
    val skipArm = playerWidth * SwipeArmFraction

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
                    .onSizeChanged { playerWidth = it.width }
                    .pointerInput(Unit) {
                        // Where this gesture has put the player, and how fast it is moving. Held here rather
                        // than read back from the animation, whose snaps are launched and may not have landed
                        // yet - so the release judges the drag exactly as the finger left it.
                        var dragOffset = 0f
                        // The finger's travel, before the rubber band takes its share of it.
                        var dragPull = 0f
                        // Past the skip threshold, towards a track: releasing now changes to it.
                        var isArmed = false
                        val velocityTracker = VelocityTracker()
                        val directionSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f

                        // Towards a track the player follows the finger up to the skip threshold and resists
                        // past it; towards none it resists from the start, showing there is nothing there.
                        fun armFor(pull: Float): Float {
                            val hasTrack = if (pull > 0) latestHasPreviousTrack else latestHasNextTrack
                            return if (hasTrack) playerWidth * SwipeArmFraction else 0f
                        }

                        fun stretchLimit() = playerWidth * (SwipeStretchMaxFraction - SwipeArmFraction)

                        // Khatmah's tick: CLOCK_TICK is felt where the lighter CONTEXT_CLICK often is not.
                        fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                        fun settle() {
                            coroutineScope.launch {
                                offsetXAnimatable.animateTo(targetValue = 0f, animationSpec = animationSpec)
                            }
                        }

                        detectHorizontalDragGestures(
                            onDragStart = {
                                // Taken up where a settle still under way has it, so a grab never jumps.
                                dragOffset = offsetXAnimatable.value
                                dragPull = rubberBandPull(dragOffset, armFor(dragOffset), stretchLimit())
                                // Unarmed even if grabbed past the threshold, so the tick confirms every skip.
                                isArmed = false
                                velocityTracker.resetTracking()
                            },
                            onDragCancel = { settle() },
                            onHorizontalDrag = { change, dragAmount ->
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                dragPull += dragAmount * directionSign
                                val arm = armFor(dragPull)
                                dragOffset = rubberBandOffset(dragPull, arm, stretchLimit())
                                val reachedArm = arm > 0f && abs(dragOffset) >= arm
                                if (reachedArm && !isArmed) tick()
                                isArmed = reachedArm
                                val targetOffset = dragOffset
                                coroutineScope.launch { offsetXAnimatable.snapTo(targetOffset) }
                            },
                            onDragEnd = {
                                // In pixels a millisecond, the speed the finger left at - away from rest is
                                // positive, so moving back towards it never counts as a swipe.
                                val outwardVelocity =
                                    velocityTracker.calculateVelocity().x * directionSign / 1000f * dragOffset.sign
                                val minDistanceThreshold = 50f
                                val velocityThreshold = (SwipeSensitivity * -8.25f) + 8.5f

                                val shouldChangeSong =
                                    (abs(dragOffset) > minDistanceThreshold && outwardVelocity > velocityThreshold) ||
                                        isArmed

                                if (shouldChangeSong) {
                                    // An armed skip has already ticked; a fling that never armed ticks now.
                                    if (dragOffset > 0 && latestHasPreviousTrack) {
                                        if (!isArmed) tick()
                                        latestOnSwipeToPrevious()
                                    } else if (dragOffset < 0 && latestHasNextTrack) {
                                        if (!isArmed) tick()
                                        latestOnSwipeToNext()
                                    }
                                }
                                settle()
                            },
                        )
                    },
        ) {
            content(offsetXAnimatable.value)

            // No skip hint towards a missing track: the pull there only shows there is nothing to change to.
            val hasTrackTowardsOffset = if (offsetXAnimatable.value > 0) hasPreviousTrack else hasNextTrack
            if (hasTrackTowardsOffset && offsetXAnimatable.value.absoluteValue > 50f) {
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
                                alpha = (offsetXAnimatable.value.absoluteValue / skipArm).coerceIn(0f, 1f),
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
            shapes = iconButtonPressShapes(),
            modifier = modifier.size(48.dp),
            enabled = enabled,
            colors = buttonColors,
            content = content,
        )
    } else {
        IconButton(
            onClick = handleClick,
            shapes = iconButtonPressShapes(),
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
