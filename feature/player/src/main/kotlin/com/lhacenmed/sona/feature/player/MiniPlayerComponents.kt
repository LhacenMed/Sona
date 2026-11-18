@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.ContainerEdge
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import com.lhacenmed.sona.core.designsystem.player.PlayerButtonGroup
import com.lhacenmed.sona.core.designsystem.player.playerButton
import com.lhacenmed.sona.feature.player.background.playerColors
import com.lhacenmed.sona.core.designsystem.player.LocalPlayerCheckedColors
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.designsystem.component.SonaMarquee
import com.lhacenmed.sona.core.designsystem.player.formatPlaybackTime
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.effect.performSonaHaptic
import com.lhacenmed.sona.core.designsystem.motion.RubberBandSettleDurationMillis
import com.lhacenmed.sona.core.designsystem.motion.RubberBandSettleEasing
import com.lhacenmed.sona.core.designsystem.motion.SwipeArmFraction
import com.lhacenmed.sona.core.designsystem.motion.SwipeStretchMaxFraction
import com.lhacenmed.sona.core.designsystem.motion.performSwipeArmHaptic
import com.lhacenmed.sona.core.designsystem.motion.rubberBandOffset
import com.lhacenmed.sona.core.designsystem.motion.rubberBandPull
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How readily a sideways swipe changes track - ArchiveTune's default `SwipeSensitivity`. */
private const val SwipeSensitivity = 0.73f

private val MiniPlayerTransportButtonSpacing = 4.dp
private val MiniPlayerButtonSize = 48.dp

@Immutable
internal data class MiniPlayerContentColors(
    val title: Color,
    val secondary: Color,
    val progress: Color,
    val progressTrack: Color,
    val artworkContainer: Color,
    val artworkBorder: Color,
    val secondaryButtonContainer: Color,
    val buttonIcon: Color,
)

/**
 * How much of the finger's travel the mini player follows while a swipe can still skip, up to the skip: lighter than the
 * band past it, but never quite under the finger - so the player always reads as held back, and the skip as something
 * pulled out of it.
 */
private const val SkipSwipeFollow = 0.6f

/** How far, as a share of its width, the mini player gives under a swipe that cannot skip. */
private const val HeldSwipeStretchFraction = 0.06f

/**
 * Where the mini player is held for [pull] - the finger's travel - across [width]: the one curve its swipe is drawn on.
 *
 * Where it [canSkip], it follows [SkipSwipeFollow] of the finger up to the skip's arm, then stiffens on the rubber band up
 * to [SwipeStretchMaxFraction] - the band's slope at the arm is the same [SkipSwipeFollow], so the resistance builds
 * without a step. Where it cannot, the band resists from the first pixel, as far as [HeldSwipeStretchFraction] only, so
 * the player answers the finger without seeming to promise anything.
 */
private fun miniPlayerSwipeOffset(pull: Float, width: Float, canSkip: Boolean): Float =
    if (canSkip) {
        rubberBandOffset(pull * SkipSwipeFollow, width * SwipeArmFraction, width * (SwipeStretchMaxFraction - SwipeArmFraction))
    } else {
        rubberBandOffset(pull * SkipSwipeFollow, 0f, width * HeldSwipeStretchFraction)
    }

/** The pull that holds the mini player at [offset] - [miniPlayerSwipeOffset] undone, so a grab takes it up where it is. */
private fun miniPlayerSwipePull(offset: Float, width: Float, canSkip: Boolean): Float =
    if (canSkip) {
        rubberBandPull(offset, width * SwipeArmFraction, width * (SwipeStretchMaxFraction - SwipeArmFraction)) / SkipSwipeFollow
    } else {
        rubberBandPull(offset, 0f, width * HeldSwipeStretchFraction) / SkipSwipeFollow
    }

/** The skip a swipe offers, drawn in the room it opens beside the player. */
private val SkipIndicatorSize = 40.dp
private val SkipIndicatorIconSize = 20.dp
private val SkipIndicatorRingWidth = 2.5.dp

/**
 * The mini player under a sideways swipe: held by [miniPlayerSwipeOffset] whatever the swipe can do, and - where it
 * [canSkip] - skipping to the previous or next track once let go past the arm, or flung, with [SwipeSkipIndicator] in the
 * room it opens saying which, and how near.
 */
@Composable
internal fun SwipeableMiniPlayerBox(
    // Off - the switch, or the seek bar holding the player - the swipe stretches the player but skips nothing.
    canSkip: Boolean,
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
                    .pointerInput(canSkip) {
                        // Where this gesture has put the player, and how fast it is moving. Held here rather
                        // than read back from the animation, whose snaps are launched and may not have landed
                        // yet - so the release judges the drag exactly as the finger left it.
                        var dragOffset = 0f
                        // The finger's travel, before the swipe's curve takes its share of it.
                        var dragPull = 0f
                        // Past the skip threshold: releasing now changes track.
                        var isArmed = false
                        val velocityTracker = VelocityTracker()
                        val directionSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f

                        // Khatmah's tick: CLOCK_TICK is felt where the lighter CONTEXT_CLICK often is not.
                        fun tick() = view.performSonaHaptic(HapticFeedbackConstants.CLOCK_TICK)

                        fun settle() {
                            coroutineScope.launch {
                                offsetXAnimatable.animateTo(targetValue = 0f, animationSpec = animationSpec)
                            }
                        }

                        detectHorizontalDragGestures(
                            onDragStart = {
                                // Taken up where a settle still under way has it, so a grab never jumps.
                                dragOffset = offsetXAnimatable.value
                                dragPull = miniPlayerSwipePull(dragOffset, playerWidth.toFloat(), canSkip)
                                // Unarmed even if grabbed past the threshold, so the tick confirms every skip.
                                isArmed = false
                                velocityTracker.resetTracking()
                            },
                            onDragCancel = { settle() },
                            onHorizontalDrag = { change, dragAmount ->
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                dragPull += dragAmount * directionSign
                                dragOffset = miniPlayerSwipeOffset(dragPull, playerWidth.toFloat(), canSkip)
                                val arm = playerWidth * SwipeArmFraction
                                val reachedArm = canSkip && arm > 0f && abs(dragOffset) >= arm
                                // Felt both ways across the arm: the skip is live, or no longer is.
                                if (reachedArm != isArmed) view.performSwipeArmHaptic(reachedArm)
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
                                    canSkip &&
                                        ((abs(dragOffset) > minDistanceThreshold && outwardVelocity > velocityThreshold) || isArmed)

                                if (shouldChangeSong) {
                                    // An armed skip has already ticked; a fling that never armed ticks now.
                                    if (dragOffset > 0) {
                                        if (!isArmed) tick()
                                        latestOnSwipeToPrevious()
                                    } else if (dragOffset < 0) {
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

            if (canSkip && skipArm > 0f && offsetXAnimatable.value != 0f) {
                SwipeSkipIndicator(offset = offsetXAnimatable::value, arm = skipArm)
            }
        }
    }
}

/**
 * Telegram's swipe indicator: the skip's glyph on a disc of its own - legible over whatever list the swipe uncovers - in
 * the middle of the room the swipe opens, ringed by how near the swipe is to skipping. Growing in as the room does, it
 * fills with the accent the moment letting go would skip. [offset] is read only where it is laid out and drawn.
 */
@Composable
private fun BoxScope.SwipeSkipIndicator(offset: () -> Float, arm: Float) {
    val toPrevious = offset() > 0f
    val isArmed by remember(arm) { derivedStateOf { abs(offset()) >= arm } }
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val discColor by animateColorAsState(
        if (isArmed) colorScheme.primary else colorScheme.secondaryContainer,
        motion.fastEffectsSpec(),
        label = "skipDisc",
    )
    val iconColor by animateColorAsState(
        if (isArmed) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
        motion.fastEffectsSpec(),
        label = "skipIcon",
    )
    val armedScale by animateFloatAsState(if (isArmed) 1.1f else 1f, motion.fastSpatialSpec(), label = "skipScale")
    val ringColor = colorScheme.primary
    val ringTrackColor = colorScheme.primary.copy(alpha = 0.2f)
    val nearness = { (abs(offset()) / arm).coerceIn(0f, 1f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .align(if (toPrevious) Alignment.CenterStart else Alignment.CenterEnd)
                // Centred in the room the swipe opens - mirrored with the layout, as the swipe itself is.
                .offset {
                    val room = abs(offset())
                    val inset = ((room - SkipIndicatorSize.toPx()) / 2f).roundToInt()
                    IntOffset(if (toPrevious) inset else -inset, 0)
                }.graphicsLayer {
                    val shown = nearness()
                    alpha = (shown * 2f).coerceAtMost(1f)
                    scaleX = lerp(0.6f, 1f, shown) * armedScale
                    scaleY = scaleX
                }.size(SkipIndicatorSize)
                .drawBehind {
                    val ring = SkipIndicatorRingWidth.toPx()
                    drawCircle(discColor, radius = size.minDimension / 2f - ring)
                    val arc = Size(size.width - ring, size.height - ring)
                    val topLeft = Offset(ring / 2f, ring / 2f)
                    drawArc(ringTrackColor, 0f, 360f, false, topLeft, arc, style = Stroke(ring))
                    drawArc(ringColor, -90f, 360f * nearness(), false, topLeft, arc, style = Stroke(ring, cap = StrokeCap.Round))
                },
    ) {
        Icon(
            imageVector = if (toPrevious) SonaIcons.SkipPrevious else SonaIcons.SkipNext,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(SkipIndicatorIconSize),
        )
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
            SonaMarquee(text = title) {
                Text(text = title, style = MaterialTheme.typography.titleMediumEmphasized, color = colors.title, maxLines = 1)
            }
        }

        AnimatedContent(
            targetState = track.artist,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "artist",
        ) { artist ->
            SonaMarquee(text = artist) {
                Text(text = artist, style = MaterialTheme.typography.bodySmall, color = colors.secondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MiniPlayerArtwork(
    coverArtUri: String?,
    progress: () -> Float,
    isLoading: Boolean,
    isPlaying: Boolean,
    showsProgress: Boolean,
    colors: MiniPlayerContentColors,
    modifier: Modifier = Modifier,
) {
    // Where the seek bar shows where the track is, the ring would say it twice: it fades out, the cover staying in place.
    val ringAlpha by animateFloatAsState(if (showsProgress) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "miniRing")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(52.dp),
    ) {
        if (isLoading) {
            CircularWavyProgressIndicator(
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ringAlpha },
                color = colors.progress,
                trackColor = colors.progressTrack,
            )
        } else {
            CircularWavyProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ringAlpha },
                color = colors.progress,
                trackColor = colors.progressTrack,
                // Flat while paused, as the expanded player's seek bar is: Material eases the wave out and back in.
                amplitude = if (isPlaying) WavyProgressIndicatorDefaults.indicatorAmplitude else { _ -> 0f },
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

/** What stands beside play and pause in the mini player: its glyph, its name, and what it does - null while it cannot. */
@Immutable
internal class MiniPlayerAction(
    val icon: ImageVector,
    val label: String,
    val onClick: (() -> Unit)?,
    /** For a toggle - the favourite - whether it is on. */
    val checked: Boolean? = null,
)

/**
 * Play and pause, and the [action] beside it, as one Material 3 Expressive button group - as the expanded player's
 * transport is: a held button widens at the other's expense. The last of them stands against the mini player's end,
 * whose roundness it keeps through a press - see [ContainerEdge].
 */
@Composable
private fun MiniPlayerControls(
    playback: PlaybackUiState,
    viewModel: PlayerViewModel,
    action: MiniPlayerAction,
    colors: MiniPlayerContentColors,
) {
    val view = LocalView.current
    val withClick = { onClick: () -> Unit ->
        {
            view.performContextClick()
            onClick()
        }
    }
    val isPaused = playback.hasEnded || !playback.isPlaying
    val playLabel = stringResource(if (isPaused) R.string.player_play else R.string.player_pause)
    // Play as the expanded player's is: on its own colour while playing, turning to the checked colours while paused.
    val playerColors = playerColors()
    val checkedColors = LocalPlayerCheckedColors.current
    val motion = MaterialTheme.motionScheme
    val playContainer by animateColorAsState(if (isPaused) checkedColors.container else playerColors.button, motion.defaultEffectsSpec(), label = "miniPlayContainer")
    val playContent by animateColorAsState(if (isPaused) checkedColors.content else playerColors.onButton, motion.defaultEffectsSpec(), label = "miniPlayContent")
    PlayerButtonGroup(spacing = MiniPlayerTransportButtonSpacing) {
        playerButton(
            icon = playback.playPauseIcon(),
            label = playLabel,
            color = playContent,
            onClick = withClick(viewModel::onTogglePlayPause),
            size = MiniPlayerButtonSize,
            iconSize = 24.dp,
            containerColor = playContainer,
        )
        playerButton(
            icon = action.icon,
            label = action.label,
            color = colors.buttonIcon,
            onClick = action.onClick?.let(withClick),
            checked = action.checked,
            size = MiniPlayerButtonSize,
            iconSize = 20.dp,
            containerColor = colors.secondaryButtonContainer,
            edge = ContainerEdge.End,
        )
    }
}

/**
 * The mini player's seek bar, as the player hands it over: where the slider is held, how it moves the track, and the
 * [action] the screen beneath puts beside play and pause, in the place of the mini player's own.
 */
internal class MiniPlayerSeekBarState(
    val sliderPosition: Long?,
    val onSeek: (Long) -> Unit,
    val onSeekFinished: () -> Unit,
    val action: TopBarAction,
)

/** How tall the mini player's slider stands, under its time, within the mini player's own height. */
private val MiniPlayerSliderHeight = 32.dp

/**
 * Where the track is, of how long, over a slider to move through it - in [sliderStyle], as the expanded player's
 * - in the place of the track's names.
 */
@Composable
private fun RowScope.MiniPlayerSeekBar(
    seekBar: MiniPlayerSeekBarState,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    sliderStyle: PlayerSliderStyle,
    colors: MiniPlayerContentColors,
) {
    val shownPosition = seekBar.sliderPosition ?: position
    Column(
        modifier =
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "${formatPlaybackTime(shownPosition)} / ${formatPlaybackTime(duration)}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.secondary,
            maxLines = 1,
        )
        StyledPlaybackSlider(
            sliderStyle = sliderStyle,
            value = shownPosition.toFloat(),
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            onValueChange = { seekBar.onSeek(it.toLong()) },
            onValueChangeFinished = seekBar.onSeekFinished,
            activeColor = colors.progress,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxWidth().height(MiniPlayerSliderHeight),
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
    seekBar: MiniPlayerSeekBarState?,
    sliderStyle: PlayerSliderStyle,
    action: MiniPlayerAction,
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
            isPlaying = playback.isPlaying,
            showsProgress = seekBar == null,
            colors = colors,
        )

        when {
            seekBar != null -> MiniPlayerSeekBar(
                seekBar = seekBar,
                position = position,
                duration = duration,
                isPlaying = playback.isPlaying,
                sliderStyle = sliderStyle,
                colors = colors,
            )
            track != null -> MiniPlayerInfo(
                track = track,
                colors = colors,
            )
            else -> Spacer(Modifier.weight(1f))
        }

        // The seek bar moves through the track, so it needs no skipping: the screen's own action stands beside play instead.
        val shownAction = seekBar?.action?.let { MiniPlayerAction(it.icon, it.label, it.onClick.takeIf { _ -> it.enabled }) } ?: action
        MiniPlayerControls(playback = playback, viewModel = viewModel, action = shownAction, colors = colors)
    }
}
