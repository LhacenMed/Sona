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
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lhacenmed.sona.core.datastore.PlayerAppearance
import com.lhacenmed.sona.core.designsystem.component.MiniPlayerSeekBarRequests
import com.lhacenmed.sona.core.designsystem.effect.rememberDeviceMusicVolumeController
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.SleepTimerState
import com.lhacenmed.sona.feature.player.background.PlayerBackground
import com.lhacenmed.sona.feature.player.background.playerColors
import com.lhacenmed.sona.feature.player.style.ExpandedPlayer
import com.lhacenmed.sona.feature.player.style.lyricsPresentation
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
 * Everything here is shared by every style - the sheets, seeking, keyboard control, the lyrics - opened in
 * the player or in a sheet of their own, as the style's `lyricsPresentation` says - and their menu, the sleep
 * timer and the track menu. What a style draws is looked up in `style/PlayerStyles.kt`.
 */
@Composable
internal fun BottomSheetPlayer(
    state: BottomSheetState,
    uiState: PlayerUiState,
    appearance: PlayerAppearance,
    sleepTimer: SleepTimerState,
    viewModel: PlayerViewModel,
    miniPlayerSeekBarRequests: MiniPlayerSeekBarRequests,
    onGoToAlbum: ((Long) -> Unit)?,
    onGoToArtist: ((Long) -> Unit)?,
    onOpenEqualizer: () -> Unit,
    onEditLyrics: ((trackId: Long) -> Unit)?,
    trackOptionsSheet: @Composable (track: Track, onDismissRequest: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    val playback = uiState.playback
    val track = uiState.currentTrack
    val playerStyle = appearance.style
    val playerColors = playerColors()

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

    val lyrics = rememberPlayerLyricsState(lyricsViewModel, presentation = playerStyle.lyricsPresentation)

    // Asked from the queue bar and from the lyrics' menu alike: a running timer is stopped, otherwise one is set.
    var isSleepTimerDialogShown by remember { mutableStateOf(false) }
    val onSleepTimerClick = {
        if (sleepTimer.isActive) viewModel.onClearSleepTimer() else isSleepTimerDialogShown = true
    }

    BackHandler(
        enabled =
            queueSheetState.isExpandedOrExpanding ||
                state.isExpandedOrExpanding,
    ) {
        when {
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

    // Both the expanded player's seek bar and the mini player's move through the track this way.
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

    BottomSheet(
        state = state,
        // A swipe up anywhere on the open player raises the queue, not only one begun on its bar - save over
        // its lyrics, which the swipe scrolls.
        swipeUpSheet = queueSheetState.takeUnless { lyrics.isOpen },
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
                appearance = appearance,
                viewModel = viewModel,
                seekBar = miniPlayerSeekBarRequests.action?.let { action ->
                    MiniPlayerSeekBarState(
                        sliderPosition = sliderPosition,
                        onSeek = onSliderValueChange,
                        onSeekFinished = onSliderValueChangeFinished,
                        action = action,
                    )
                },
            )
        },
    ) {
        PlayerBackground(
            style = appearance.background,
            coverArtUri = track?.coverArtUri,
            customBackground = appearance.customBackground,
        )

        if (track != null) {
            val artistsById by viewModel.artistsById.collectAsState()
            val creditedArtists = remember(track, artistsById) { track.artistIds.mapNotNull(artistsById::get) }
            val titleActions = rememberPlayerTitleActions(track, creditedArtists, state, onGoToAlbum, onGoToArtist)
            val onMenuClick = { menuTrack = track }

            playerStyle.ExpandedPlayer(
                track = track,
                uiState = uiState,
                appearance = appearance,
                colors = playerColors,
                isLoading = isLoading,
                isPlayerExpanded = state.isExpanded,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                queueBarHeight = queueSheetState.collapsedBound,
                titleActions = titleActions,
                lyrics = lyrics,
                onMenuClick = onMenuClick,
                onCollapse = state::collapseSoft,
                // Collapsed on the way, as going to the album or the artist is.
                onOpenEqualizer = {
                    state.collapseSoft()
                    onOpenEqualizer()
                },
                viewModel = viewModel,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )
        }

        // Lyrics in the player are drawn under the queue; their sheet covers it, as it does the whole player.
        if (track != null && lyrics.opensInPlayer) {
            PlayerLyrics(
                state = lyrics,
                track = track,
                durationMs = duration,
                contentColor = playerColors.content,
                positionMsProvider = viewModel::currentPositionMs,
                sliderPositionProvider = { sliderPosition },
                seekBarPositionMs = { sliderPosition ?: position },
                lyricsSyncOffset = lyricsSyncOffset,
                onSeek = viewModel::onSeek,
                lyricsViewModel = lyricsViewModel,
            )
        }

        Queue(
            state = queueSheetState,
            playerBottomSheetState = state,
            uiState = uiState,
            playerStyle = playerStyle,
            barContentColor = playerColors.content,
            sleepTimer = sleepTimer,
            durationMs = duration,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            onBackgroundColor = MaterialTheme.colorScheme.onSurface,
            onMenuClick = { menuTrack = it },
            onSleepTimerClick = onSleepTimerClick,
            lyrics = lyrics,
            viewModel = viewModel,
        )

        if (track != null && !lyrics.opensInPlayer) {
            LyricsSheetTransition(
                state = lyrics,
                backHandlerEnabled = lyrics.isOpen && state.isExpandedOrExpanding,
                track = track,
                playback = playback,
                durationMs = duration,
                appearance = appearance,
                lyricsSyncOffset = lyricsSyncOffset,
                viewModel = viewModel,
                lyricsViewModel = lyricsViewModel,
            )
        }
    }

    if (track != null && lyrics.isMenuShown) {
        LyricsMenuSheet(
            track = track,
            isFavorite = uiState.isCurrentTrackFavorite,
            onToggleFavorite = if (uiState.canFavorite) viewModel::onToggleFavorite else null,
            onOpenQueue = {
                // The queue rises over lyrics in the player, but under their sheet - which makes way for it.
                if (!lyrics.opensInPlayer) lyrics.close()
                queueSheetState.expandSoft()
            },
            onSleepTimerClick = onSleepTimerClick,
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
            showPlayerControls = lyrics.showsPlayerControls,
            onShowPlayerControlsChange = lyricsViewModel::setShowLyricsPlayerControls,
            onEditLyrics = onEditLyrics?.let { editLyrics -> { editLyrics(track.id) } },
            onDismissRequest = lyrics::hideMenu,
        )
    }

    if (isSleepTimerDialogShown) {
        SleepTimerDialog(
            onDismiss = { isSleepTimerDialogShown = false },
            onConfirm = { minutes ->
                isSleepTimerDialogShown = false
                viewModel.onStartSleepTimer(minutes)
            },
            onEndOfSong = {
                isSleepTimerDialogShown = false
                viewModel.onStartSleepTimerAtEndOfTrack()
            },
        )
    }

    menuTrack?.let { menuFor ->
        trackOptionsSheet(menuFor) { menuTrack = null }
    }
}
