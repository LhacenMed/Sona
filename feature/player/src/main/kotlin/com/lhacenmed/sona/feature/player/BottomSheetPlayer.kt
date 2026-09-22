package com.lhacenmed.sona.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.SleepTimerState
import com.lhacenmed.sona.feature.player.style.ExpandedPlayer
import com.lhacenmed.sona.feature.player.style.sheetColor
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SeekbarSettleToleranceMs = 1_500L
private const val PositionPollIntervalMs = 100L
private const val KeyboardSeekStepMs = 5_000L
private const val KeyboardVolumeStep = 0.05f

/**
 * The full player: the mini player as the sheet's collapsed content, expanding into the chosen player
 * style over its queue. Ported from ArchiveTune's `BottomSheetPlayer`.
 *
 * Everything here is shared by every style - the sheets, seeking, keyboard control, lyrics and the
 * track menu. What a style draws is looked up in `style/PlayerStyles.kt`.
 */
@Composable
internal fun BottomSheetPlayer(
    state: BottomSheetState,
    uiState: PlayerUiState,
    playerStyle: PlayerStyle,
    sliderStyle: PlayerSliderStyle,
    sleepTimer: SleepTimerState,
    viewModel: PlayerViewModel,
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (Long) -> Unit,
    trackOptionsSheet: @Composable (track: Track, onDismissRequest: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = uiState.playback
    val track = uiState.currentTrack

    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()

    val duration = track?.durationMs?.takeIf { it > 0L } ?: playback.durationMs
    val latestDuration by rememberUpdatedState(duration)
    var position by remember(track?.id) { mutableLongStateOf(viewModel.currentPositionMs()) }
    var lyricsSyncOffset by rememberSaveable(track?.id) { mutableIntStateOf(0) }
    var sliderPosition by remember(track?.id) { mutableStateOf<Long?>(null) }
    var isUserSeeking by remember(track?.id) { mutableStateOf(false) }

    // Track loading state: when buffering or when user is seeking
    val isLoading = playback.isBuffering || sliderPosition != null

    // Followed only while the activity is started: a player in an activity left behind in the back stack
    // has nothing to show and nobody to show it to.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(track?.id, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val currentPlayerPosition = viewModel.currentPositionMs()
                position = currentPlayerPosition
                if (!isUserSeeking) {
                    sliderPosition?.let { targetPosition ->
                        val clampedTargetPosition =
                            if (latestDuration > 0L) {
                                targetPosition.coerceIn(0L, latestDuration)
                            } else {
                                targetPosition.coerceAtLeast(0L)
                            }
                        if (abs(currentPlayerPosition - clampedTargetPosition) <= SeekbarSettleToleranceMs) {
                            sliderPosition = null
                        }
                    }
                }
                delay(PositionPollIntervalMs)
            }
        }
    }

    val dismissedBound = QueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState =
        rememberBottomSheetState(
            dismissedBound = dismissedBound,
            expandedBound = state.expandedBound,
            collapsedBound = dismissedBound,
            initialAnchor = DISMISSED_ANCHOR,
        )

    var isLyricsSheetVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(
        enabled =
            queueSheetState.isExpandedOrExpanding ||
                state.isExpandedOrExpanding,
    ) {
        when {
            isLyricsSheetVisible && state.isExpandedOrExpanding -> isLyricsSheetVisible = false
            queueSheetState.isExpandedOrExpanding -> queueSheetState.collapseSoft()
            state.isExpandedOrExpanding -> state.collapseSoft()
        }
    }

    var menuTrack by remember { mutableStateOf<Track?>(null) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) {
            focusRequester.requestFocus()
        }
    }

    BottomSheet(
        state = state,
        // A swipe up anywhere on the open player raises the queue, not only one begun on its bar.
        swipeUpSheet = queueSheetState,
        modifier =
            modifier
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown || state.isCollapsed) return@onKeyEvent false

                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            viewModel.onSeek((viewModel.currentPositionMs() - KeyboardSeekStepMs).coerceAtLeast(0))
                            true
                        }

                        Key.DirectionRight -> {
                            viewModel.onSeek((viewModel.currentPositionMs() + KeyboardSeekStepMs).coerceAtMost(duration))
                            true
                        }

                        Key.DirectionUp -> {
                            deviceMusicVolumeController.setVolumeFraction(
                                (deviceMusicVolumeController.volumeFraction + KeyboardVolumeStep).coerceAtMost(1f),
                            )
                            true
                        }

                        Key.DirectionDown -> {
                            deviceMusicVolumeController.setVolumeFraction(
                                (deviceMusicVolumeController.volumeFraction - KeyboardVolumeStep).coerceAtLeast(0f),
                            )
                            true
                        }

                        Key.Spacebar -> {
                            viewModel.onTogglePlayPause()
                            true
                        }

                        Key.N -> {
                            if (keyEvent.isShiftPressed) {
                                viewModel.onSkipNext()
                                true
                            } else {
                                false
                            }
                        }

                        Key.P -> {
                            if (keyEvent.isShiftPressed) {
                                viewModel.onSkipPrevious()
                                true
                            } else {
                                false
                            }
                        }

                        Key.L -> {
                            viewModel.onToggleFavorite()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
        backgroundColor = run {
            val progress =
                ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
                    .coerceIn(0f, 1f)
            // Only start fading when very close to dismissal (last 20%)
            val fadeProgress =
                if (progress < 0.2f) {
                    ((0.2f - progress) / 0.2f).coerceIn(0f, 1f)
                } else {
                    0f
                }
            playerStyle.sheetColor().copy(alpha = 1f - fadeProgress)
        },
        onDismiss = viewModel::onStopAndClearQueue,
        collapsedContent = {
            MiniPlayer(
                track = track,
                playback = playback,
                position = position,
                duration = duration,
                viewModel = viewModel,
            )
        },
    ) {
        val onSliderValueChange: (Long) -> Unit = {
            isUserSeeking = true
            sliderPosition = it
        }
        val onSliderValueChangeFinished: () -> Unit = {
            sliderPosition?.let {
                viewModel.onSeek(it)
                position = it
            }
            isUserSeeking = false
        }

        if (track != null) {
            val titleActions = rememberPlayerTitleActions(track, state, onGoToAlbum, onGoToArtist)
            val onMenuClick = { menuTrack = track }

            playerStyle.ExpandedPlayer(
                track = track,
                uiState = uiState,
                sliderStyle = sliderStyle,
                isLoading = isLoading,
                isPlayerExpanded = state.isExpanded,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                queueBarHeight = queueSheetState.collapsedBound,
                titleActions = titleActions,
                onMenuClick = onMenuClick,
                viewModel = viewModel,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )
        }

        Queue(
            state = queueSheetState,
            playerBottomSheetState = state,
            uiState = uiState,
            playerStyle = playerStyle,
            sleepTimer = sleepTimer,
            durationMs = duration,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            onBackgroundColor = MaterialTheme.colorScheme.onSurface,
            onMenuClick = { menuTrack = it },
            onShowLyrics = { isLyricsSheetVisible = true },
            viewModel = viewModel,
        )

        track?.let { currentTrack ->
            LyricsSheetTransition(
                visible = isLyricsSheetVisible,
                backHandlerEnabled = isLyricsSheetVisible && state.isExpandedOrExpanding,
                track = currentTrack,
                playback = playback,
                durationMs = duration,
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                onDismiss = { isLyricsSheetVisible = false },
                viewModel = viewModel,
            )
        }
    }

    menuTrack?.let { menuFor ->
        trackOptionsSheet(menuFor) { menuTrack = null }
    }
}
