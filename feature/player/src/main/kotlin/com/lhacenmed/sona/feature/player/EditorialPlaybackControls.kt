package com.lhacenmed.sona.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class EditorialPlaybackButton { PREVIOUS, PLAY_PAUSE, NEXT }

private const val BaseWeight = 1f
private const val ExpansionWeight = 1.1f
private const val CompressionWeight = 0.65f
private const val ReleaseDelayMs = 220L
private const val SkipReleaseDelayMs = 600L
private const val SkipCommandDelayMs = 180L
private val PlayPauseCornerPlaying = 60.dp
private val PlayPauseCornerPaused = 26.dp
private val PlayPauseIconSize = 36.dp
private val SkipIconSize = 32.dp

/**
 * The Editorial player's transport row: the pressed button swells while the others give way, and
 * play/pause morphs its corners with the playing state. Ported from ArchiveTune's `V9AnimatedPlaybackControls`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EditorialPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    height: Dp,
    pressAnimationSpec: AnimationSpec<Float>,
    colorOtherButtons: Color,
    colorPlayPause: Color,
    tintPlayPauseIcon: Color,
    tintOtherIcons: Color,
    modifier: Modifier = Modifier,
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<EditorialPlaybackButton?>(null) }
    var clickTrigger by remember { mutableIntStateOf(0) }
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)
    val latestLastClicked by rememberUpdatedState(newValue = lastClicked)
    val isPlayPauseLocked =
        lastClicked == EditorialPlaybackButton.NEXT || lastClicked == EditorialPlaybackButton.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val motionScheme = remember { MotionScheme.expressive() }
    val defaultSpatialDpSpec = remember { motionScheme.defaultSpatialSpec<Dp>() }

    LaunchedEffect(lastClicked, clickTrigger) {
        if (lastClicked != null) {
            val delayTime = when (lastClicked) {
                EditorialPlaybackButton.NEXT, EditorialPlaybackButton.PREVIOUS -> SkipReleaseDelayMs
                else -> ReleaseDelayMs
            }
            delay(delayTime)
            lastClicked = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }

        val shouldDelay = latestLastClicked != EditorialPlaybackButton.PLAY_PAUSE
        if (shouldDelay) {
            delay(ReleaseDelayMs)
        }
        if (!latestIsPlayingProvider()) {
            pendingPlayPauseState = false
        }
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            fun weightFor(button: EditorialPlaybackButton): Float = when (lastClicked) {
                button -> ExpansionWeight
                null -> BaseWeight
                else -> CompressionWeight
            }

            val prevWeight by animateFloatAsState(
                targetValue = weightFor(EditorialPlaybackButton.PREVIOUS),
                animationSpec = pressAnimationSpec,
                label = "prevWeight",
            )
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorOtherButtons)
                    .clickable {
                        lastClicked = EditorialPlaybackButton.PREVIOUS
                        clickTrigger++
                        coroutineScope.launch {
                            delay(SkipCommandDelayMs)
                            onPrevious()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = stringResource(R.string.player_previous),
                    tint = tintOtherIcons,
                    modifier = Modifier.size(SkipIconSize),
                )
            }

            val playWeight by animateFloatAsState(
                targetValue = weightFor(EditorialPlaybackButton.PLAY_PAUSE),
                animationSpec = pressAnimationSpec,
                label = "playWeight",
            )
            val playCorner by animateDpAsState(
                targetValue = if (!playPauseVisualState) PlayPauseCornerPlaying else PlayPauseCornerPaused,
                animationSpec = defaultSpatialDpSpec,
                label = "playCorner",
            )
            Box(
                modifier = Modifier
                    .weight(playWeight)
                    .fillMaxHeight()
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(playCorner)
                    }
                    .background(colorPlayPause)
                    .clickable {
                        lastClicked = EditorialPlaybackButton.PLAY_PAUSE
                        clickTrigger++
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = playPauseVisualState,
                    animationSpec = motionScheme.fastEffectsSpec(),
                    label = "editorialPlayPauseCrossfade",
                ) { playing ->
                    Icon(
                        painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(if (playing) R.string.player_pause else R.string.player_play),
                        tint = tintPlayPauseIcon,
                        modifier = Modifier.size(PlayPauseIconSize),
                    )
                }
            }

            val nextWeight by animateFloatAsState(
                targetValue = weightFor(EditorialPlaybackButton.NEXT),
                animationSpec = pressAnimationSpec,
                label = "nextWeight",
            )
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorOtherButtons)
                    .clickable {
                        lastClicked = EditorialPlaybackButton.NEXT
                        clickTrigger++
                        coroutineScope.launch {
                            delay(SkipCommandDelayMs)
                            onNext()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = stringResource(R.string.player_next),
                    tint = tintOtherIcons,
                    modifier = Modifier.size(SkipIconSize),
                )
            }
        }
    }
}
