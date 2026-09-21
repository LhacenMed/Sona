@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.LocalDragHandle
import com.lhacenmed.sona.core.designsystem.component.SonaTrackRow
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.SleepTimerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The queue: a bar along the bottom of the full player - queue, sleep timer and lyrics, in the player
 * style's own shape - that expands into the queue itself, where rows play, and, once a long press has
 * opened the queue for reordering, drag and swipe away. Ported from ArchiveTune's `Queue`.
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
    textBackgroundColor: Color,
    onMenuClick: (Track) -> Unit,
    onShowLyrics: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val playback = uiState.playback
    val currentTrack = uiState.currentTrack

    // Whether the queue is open for reordering: a long press on a row opens it, and system back or
    // the header's close button is the way out. It is the only mode the queue has - a row is played,
    // dragged or swiped away, never gathered into a selection.
    var reordering by remember { mutableStateOf(false) }

    if (reordering) {
        BackHandler {
            reordering = false
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    val onRemoveWithUndo: (QueueTrack) -> Unit = { item ->
        val items = listOf(item)
        viewModel.onRemoveQueueItems(items)
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
                    viewModel.onRestoreQueueItems(items)
                }
            }
    }

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
            when (playerStyle) {
                PlayerStyle.MINIMAL -> {
                    MinimalQueueBar(
                        textBackgroundColor = textBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = onSleepTimerClick,
                        onShowLyrics = onShowLyrics,
                        onMenuClick = { currentTrack?.let(onMenuClick) },
                    )
                }

                PlayerStyle.CINEMATIC -> {
                    CinematicQueueBar(
                        textBackgroundColor = textBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = onSleepTimerClick,
                        onShowLyrics = onShowLyrics,
                    )
                }

                PlayerStyle.CLASSIC -> {
                    ClassicQueueBar(
                        textBackgroundColor = textBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = onSleepTimerClick,
                        onShowLyrics = onShowLyrics,
                    )
                }

                PlayerStyle.EDITORIAL -> {
                    EditorialQueueBar(
                        textBackgroundColor = textBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        shuffleEnabled = playback.shuffleEnabled,
                        repeatMode = playback.repeatMode,
                        onShuffleClick = viewModel::onToggleShuffle,
                        onRepeatModeClick = viewModel::onCycleRepeatMode,
                        onMenuClick = { currentTrack?.let(onMenuClick) },
                        onSleepTimerClick = onSleepTimerClick,
                    )
                }

                PlayerStyle.IMMERSIVE, PlayerStyle.IMMERSIVE_EXTENDED -> {
                    ImmersiveQueueBar(
                        textBackgroundColor = textBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onShowLyrics = onShowLyrics,
                        onSleepTimerClick = onSleepTimerClick,
                    )
                }
            }

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
        // The order the rows are drawn in, which a drag rearranges at once - the player is only told
        // once the finger lifts, and the queue it sends back takes over again here.
        val stagedQueue =
            remember {
                mutableStateListOf<QueueTrack>().apply { addAll(queue) }
            }

        val lazyListState = rememberLazyListState()
        // A drag down with the list already at its top closes the sheet, wherever on the list it
        // starts and whether or not the queue is unlocked - swiping a row away is a horizontal
        // gesture, so it can never claim a vertical drag of the list first.
        val sheetScrollConnection =
            remember(state, lazyListState) {
                state.nestedScrollConnection { !lazyListState.canScrollBackward }
            }

        val currentPlayingKey =
            remember(uiState.currentQueueIndex, queue) {
                queue.getOrNull(uiState.currentQueueIndex)?.entry?.key
            }

        var dragInfo by remember { mutableStateOf<QueueDragInfo?>(null) }

        // Rows are only draggable once reordering has been opened, so nothing on an ordinary queue
        // listens for a drag and nothing can take one from the list or the sheet. A reorder moves items
        // in the player's own order, which is the order shown only while not shuffling - so a shuffled
        // queue cannot be reordered, and a long press over one does not pretend otherwise.
        val canOpenReorder = !playback.shuffleEnabled
        val canReorder = reordering && canOpenReorder

        // Shuffle turned on while reordering leaves a mode with nothing to drag; close it.
        LaunchedEffect(canOpenReorder) {
            if (!canOpenReorder) reordering = false
        }

        val reorderableState =
            rememberReorderableLazyListState(
                lazyListState = lazyListState,
                scrollThresholdPadding =
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom)
                        .add(WindowInsets(bottom = QueueItemHeight))
                        .asPaddingValues(),
            ) onMove@{ from, to ->
                val fromQueueIndex = from.index
                val toQueueIndex = to.index
                if (
                    fromQueueIndex !in stagedQueue.indices ||
                    toQueueIndex !in stagedQueue.indices
                ) {
                    return@onMove
                }

                val draggedItemKey = dragInfo?.draggedItemKey ?: stagedQueue[fromQueueIndex].entry.key
                val actualFromQueueIndex = stagedQueue.indexOfFirst { it.entry.key == draggedItemKey }
                if (actualFromQueueIndex == -1) return@onMove

                stagedQueue.add(toQueueIndex, stagedQueue.removeAt(actualFromQueueIndex))
                dragInfo =
                    QueueDragInfo(
                        draggedItemKey = draggedItemKey,
                        destination =
                            if (toQueueIndex == 0) {
                                QueueDragDestination.Start
                            } else {
                                QueueDragDestination.After(itemKey = stagedQueue[toQueueIndex - 1].entry.key)
                            },
                    )
            }

        // A finished drag is sent to the player; anything else that changed the queue is taken as it is.
        LaunchedEffect(queue, reorderableState.isAnyItemDragging) {
            if (reorderableState.isAnyItemDragging) return@LaunchedEffect

            val completedDrag = dragInfo
            if (completedDrag != null) {
                val sourceIndex = queue.indexOfFirst { it.entry.key == completedDrag.draggedItemKey }
                val destinationIndex = completedDrag.destination.resolveIndex(queue, sourceIndex)
                dragInfo = null

                if (
                    sourceIndex != -1 &&
                    destinationIndex != null &&
                    sourceIndex != destinationIndex
                ) {
                    viewModel.onMoveQueueItem(queue[sourceIndex], queue[destinationIndex])
                    return@LaunchedEffect
                }
            }

            Snapshot.withMutableSnapshot {
                stagedQueue.clear()
                stagedQueue.addAll(queue)
            }
        }

        LaunchedEffect(state.isCollapsed, scrollToCurrentRequested, currentPlayingKey) {
            if (!state.isCollapsed && scrollToCurrentRequested && currentPlayingKey != null) {
                val index = stagedQueue.indexOfFirst { it.entry.key == currentPlayingKey }
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
                    isReordering = canReorder,
                    backgroundColor = backgroundColor,
                    onBackgroundColor = onBackgroundColor,
                    onToggleFavorite = viewModel::onToggleFavorite,
                    onExitReorder = { reordering = false },
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
                        items = stagedQueue,
                        key = { item -> item.entry.key },
                        contentType = { "queue_item" },
                    ) { item ->
                        ReorderableItem(
                            state = reorderableState,
                            key = item.entry.key,
                        ) {
                            val currentItem by rememberUpdatedState(item)
                            val isActive = item.entry.key == currentPlayingKey
                            val dismissBoxState =
                                rememberSwipeToDismissBoxState(
                                    positionalThreshold = { totalDistance -> totalDistance },
                                )

                            var processedDismiss by remember { mutableStateOf(false) }
                            LaunchedEffect(dismissBoxState.currentValue) {
                                val dismissValue = dismissBoxState.currentValue
                                if (!processedDismiss && (
                                        dismissValue == SwipeToDismissBoxValue.StartToEnd ||
                                            dismissValue == SwipeToDismissBoxValue.EndToStart
                                    )
                                ) {
                                    processedDismiss = true
                                    onRemoveWithUndo(currentItem)
                                }
                                if (dismissValue == SwipeToDismissBoxValue.Settled) {
                                    processedDismiss = false
                                }
                            }

                            val content: @Composable () -> Unit = {
                                // The handle reaches the row through the composition, the way every
                                // reorderable list in the app hands it over.
                                CompositionLocalProvider(
                                    LocalDragHandle provides if (canReorder) Modifier.draggableHandle() else null,
                                ) {
                                    SonaTrackRow(
                                        track = item.track,
                                        isCurrent = { isActive },
                                        isPlaying = { playback.isPlaying && isActive },
                                        selection = null,
                                        selectionKey = null,
                                        // A tap plays, whatever mode the queue is in: the rows never
                                        // stop being the queue.
                                        onClick = {
                                            if (isActive) {
                                                viewModel.onTogglePlayPause()
                                            } else {
                                                viewModel.onPlayQueueItem(currentItem)
                                            }
                                        },
                                        onLongClick = {
                                            if (canOpenReorder) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                reordering = true
                                            }
                                        },
                                        onOpenOptions = { onMenuClick(item.track) },
                                        containerColor = backgroundColor,
                                    )
                                }
                            }

                            // A row is only swiped away once reordering has been opened, the same
                            // moment it becomes draggable: an ordinary queue answers taps alone.
                            if (canReorder) {
                                SwipeToDismissBox(
                                    state = dismissBoxState,
                                    backgroundContent = {},
                                ) {
                                    content()
                                }
                            } else {
                                content()
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

@Immutable
private data class QueueDragInfo(
    val draggedItemKey: String,
    val destination: QueueDragDestination,
)

@Immutable
private sealed interface QueueDragDestination {
    data object Start : QueueDragDestination

    data class After(
        val itemKey: String,
    ) : QueueDragDestination
}

private fun QueueDragDestination.resolveIndex(
    queue: List<QueueTrack>,
    sourceIndex: Int,
): Int? =
    when (this) {
        QueueDragDestination.Start -> if (queue.isEmpty()) null else 0
        is QueueDragDestination.After -> {
            val anchorIndex = queue.indexOfFirst { it.entry.key == itemKey }
            when {
                sourceIndex !in queue.indices -> null
                anchorIndex == -1 -> null
                sourceIndex < anchorIndex -> anchorIndex
                else -> (anchorIndex + 1).coerceAtMost(queue.lastIndex)
            }
        }
    }
