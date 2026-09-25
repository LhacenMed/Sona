@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.LocalDragHandle
import com.lhacenmed.sona.core.designsystem.component.SonaTrackRow
import com.lhacenmed.sona.core.designsystem.component.swipe.LocalSwipeActions
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeAction
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeActionTone
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeActions
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.SleepTimerState
import com.lhacenmed.sona.feature.player.style.QueueBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The queue: a bar along the bottom of the full player - queue, sleep timer and lyrics, in the player
 * style's own shape - that expands into the queue itself, where every row plays when tapped, moves by its
 * handle, and is swiped towards its end to play next or towards its start to leave the queue, shuffled or
 * not. Ported from ArchiveTune's `Queue`.
 *
 * The rows are the queue in the order it plays, and every change to them is made in that order - the
 * shuffle order while shuffling - so a row lands exactly where it was dropped, and an undone removal
 * exactly where it was.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Queue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    uiState: PlayerUiState,
    playerStyle: PlayerStyle,
    sleepTimer: SleepTimerState,
    durationMs: Long,
    backgroundColor: Color,
    onBackgroundColor: Color,
    onMenuClick: (Track) -> Unit,
    onShowLyrics: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playback = uiState.playback
    val currentTrack = uiState.currentTrack

    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerEnabled = sleepTimer.isActive
    var sleepTimerTimeLeft by remember { mutableLongStateOf(0L) }
    val latestDurationMs by rememberUpdatedState(durationMs)

    LaunchedEffect(sleepTimer) {
        if (sleepTimer.isActive) {
            while (isActive) {
                sleepTimerTimeLeft =
                    (sleepTimer.pausesAtMs?.let { it - System.currentTimeMillis() }
                        ?: (latestDurationMs - viewModel.currentPositionMs())).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    var scrollToCurrentRequested by remember { mutableStateOf(true) }
    val openQueue =
        remember(playerBottomSheetState, state) {
            {
                scrollToCurrentRequested = true
                if (!playerBottomSheetState.isExpandedOrExpanding) {
                    playerBottomSheetState.expandSoft()
                }
                state.expandSoft()
            }
        }
    val onSleepTimerClick = {
        if (sleepTimerEnabled) {
            viewModel.onClearSleepTimer()
        } else {
            showSleepTimerDialog = true
        }
    }
    BottomSheet(
        state = state,
        backgroundColor = Color.Unspecified,
        modifier = modifier,
        onCollapsedContentClick = openQueue,
        // The queue list owns every gesture over it, handing the sheet only what it cannot scroll;
        // the header below is what the sheet itself is dragged by.
        isContentDraggable = false,
        collapsedContent = {
            playerStyle.QueueBar(
                sleepTimerEnabled = sleepTimerEnabled,
                sleepTimerTimeLeft = sleepTimerTimeLeft,
                onExpandQueue = openQueue,
                onSleepTimerClick = onSleepTimerClick,
                onShowLyrics = onShowLyrics,
            )

            if (showSleepTimerDialog) {
                SleepTimerDialog(
                    onDismiss = { showSleepTimerDialog = false },
                    onConfirm = { minutes ->
                        showSleepTimerDialog = false
                        viewModel.onStartSleepTimer(minutes)
                    },
                    onEndOfSong = {
                        showSleepTimerDialog = false
                        viewModel.onStartSleepTimerAtEndOfTrack()
                    },
                )
            }
        },
    ) {
        val queue = uiState.queue
        val latestQueue by rememberUpdatedState(queue)

        // The order a drag has arranged the rows in: set on its first move, and shown until the player's
        // queue answers the drop. Otherwise null, and the rows are the queue itself - a removal, an undo
        // or a "play next" shows the moment the player has made it, which for a removal is the next frame.
        var arrangedQueue by remember { mutableStateOf<SnapshotStateList<QueueTrack>?>(null) }
        var draggedKey by remember { mutableStateOf<String?>(null) }
        val rows = arrangedQueue ?: queue

        // Where the row sat is read from the queue as it is now, by its key, so undoing puts it back
        // exactly there - shuffled or not.
        val onRemoveWithUndo: (QueueTrack) -> Unit = { item ->
            val playPosition = latestQueue.indexOfFirst { it.entry.key == item.entry.key }
            viewModel.onRemoveQueueItem(item)
            dismissJob?.cancel()
            dismissJob =
                coroutineScope.launch {
                    val snackbarResult =
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.player_removed_song_from_queue, item.track.title),
                            actionLabel = context.getString(R.string.player_undo),
                            duration = SnackbarDuration.Short,
                        )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        viewModel.onRestoreQueueItem(item, playPosition)
                    }
                }
        }

        val lazyListState = rememberLazyListState()
        // A drag down with the list already at its top closes the sheet, wherever on the list it
        // starts - swiping a row away is a horizontal gesture, and moving one starts on its handle,
        // so neither can claim a vertical drag of the list first.
        val sheetScrollConnection =
            remember(state, lazyListState) {
                state.nestedScrollConnection { !lazyListState.canScrollBackward }
            }

        val currentPlayingKey =
            remember(uiState.currentQueueIndex, queue) {
                queue.getOrNull(uiState.currentQueueIndex)?.entry?.key
            }

        // The list holds nothing but the rows, so a row's index in it is its place in [rows].
        val reorderableState =
            rememberReorderableLazyListState(
                lazyListState = lazyListState,
                scrollThresholdPadding =
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom)
                        .add(WindowInsets(bottom = QueueItemHeight))
                        .asPaddingValues(),
            ) { from, to ->
                val arranged = arrangedQueue ?: queue.toMutableStateList().also { arrangedQueue = it }
                if (draggedKey == null) draggedKey = from.key as String
                arranged.add(to.index, arranged.removeAt(from.index))
            }

        // The drop: the player is asked to make the same move - out of the dragged row's place in the
        // queue, into its place in the arranged order - and the arranged order stays up until it has.
        // A drop where the drag began, or on a queue that changed under the drag, just shows the queue.
        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (reorderableState.isAnyItemDragging) return@LaunchedEffect
            val arranged = arrangedQueue ?: return@LaunchedEffect
            val key = draggedKey
            draggedKey = null
            val fromPosition = queue.indexOfFirst { it.entry.key == key }
            val toPosition = arranged.indexOfFirst { it.entry.key == key }
            val isSameQueue = queue.mapTo(HashSet()) { it.entry.key } == arranged.mapTo(HashSet()) { it.entry.key }
            if (isSameQueue && fromPosition != toPosition) {
                viewModel.onMoveQueueItem(fromPosition, toPosition)
            } else {
                arrangedQueue = null
            }
        }

        // The player's answer - or any other change to the queue - takes over from an arranged order.
        LaunchedEffect(queue) {
            if (!reorderableState.isAnyItemDragging) arrangedQueue = null
        }

        LaunchedEffect(state.isCollapsed, scrollToCurrentRequested, currentPlayingKey) {
            if (!state.isCollapsed && scrollToCurrentRequested && currentPlayingKey != null) {
                val index = rows.indexOfFirst { it.entry.key == currentPlayingKey }
                if (index != -1) {
                    lazyListState.scrollToItem(index)
                    scrollToCurrentRequested = false
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CurrentSongHeader(
                    sheetState = state,
                    track = currentTrack,
                    isFavorite = uiState.isCurrentTrackFavorite,
                    repeatMode = playback.repeatMode,
                    shuffleEnabled = playback.shuffleEnabled,
                    backgroundColor = backgroundColor,
                    onBackgroundColor = onBackgroundColor,
                    onToggleFavorite = viewModel::onToggleFavorite,
                    onRepeatClick = viewModel::onCycleRepeatMode,
                    onShuffleClick = viewModel::onToggleShuffle,
                )

                LazyColumn(
                    state = lazyListState,
                    // The gap under the divider is the list's own padding rather than a first row of
                    // empty space, so it scrolls away with the rows and every row's index is its
                    // place in the queue.
                    contentPadding =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            .add(WindowInsets(top = 8.dp, bottom = QueueItemHeight + 8.dp))
                            .asPaddingValues(),
                    modifier =
                        Modifier
                            .weight(1f)
                            .nestedScroll(sheetScrollConnection),
                ) {
                    items(
                        items = rows,
                        key = { item -> item.entry.key },
                        contentType = { "queue_item" },
                    ) { item ->
                        ReorderableItem(
                            state = reorderableState,
                            key = item.entry.key,
                            // A row comes and goes at once, and only its neighbours move, sliding to close
                            // the gap or to open it: a fade would leave the row that went half there, over
                            // the ones taking its place.
                            animateItemModifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                        ) {
                            val currentItem by rememberUpdatedState(item)
                            val isActive = item.entry.key == currentPlayingKey
                            // Towards the row's end it plays next - save the track playing, which is already
                            // where it plays - and towards its start it leaves the queue, with an undo.
                            val swipeActions =
                                remember(isActive) {
                                    SwipeActions(
                                        startToEnd = if (isActive) {
                                            null
                                        } else {
                                            SwipeAction(SonaIcons.PlayNext, SwipeActionTone.Primary) {
                                                viewModel.onPlayQueueItemNext(currentItem)
                                            }
                                        },
                                        endToStart = SwipeAction(SonaIcons.Delete, SwipeActionTone.Danger) {
                                            onRemoveWithUndo(currentItem)
                                        },
                                    )
                                }

                            // The handle and the swipe reach the row through the composition, the way
                            // every list in the app hands them over.
                            CompositionLocalProvider(
                                LocalDragHandle provides Modifier.draggableHandle(),
                                LocalSwipeActions provides swipeActions,
                            ) {
                                SonaTrackRow(
                                    track = item.track,
                                    isCurrent = { isActive },
                                    isPlaying = { playback.isPlaying && isActive },
                                    selection = null,
                                    selectionKey = null,
                                    onClick = {
                                        if (isActive) {
                                            viewModel.onTogglePlayPause()
                                        } else {
                                            viewModel.onPlayQueueItem(currentItem)
                                        }
                                    },
                                    onOpenOptions = { onMenuClick(item.track) },
                                    containerColor = backgroundColor,
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                val bottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                        Modifier
                            .padding(bottom = QueueItemHeight + bottomInset)
                            .align(Alignment.BottomCenter),
                )
            }
        }
    }
}
