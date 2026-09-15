@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
 * style's own shape - that expands into the queue itself, where rows play, reorder, swipe away and select.
 * Ported from ArchiveTune's `Queue`.
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
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val playback = uiState.playback
    val currentTrack = uiState.currentTrack

    val selectedItems = remember { mutableStateListOf<QueueTrack>() }
    var selection by remember { mutableStateOf(false) }

    fun clearSelection() {
        selection = false
        selectedItems.clear()
    }

    if (selection) {
        BackHandler {
            clearSelection()
        }
    }

    var locked by rememberSaveable { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    val onRemoveMultipleWithUndo: (List<QueueTrack>) -> Unit = { items ->
        if (items.isNotEmpty()) {
            viewModel.onRemoveQueueItems(items)
            dismissJob?.cancel()
            dismissJob =
                coroutineScope.launch {
                    val snackbarResult =
                        snackbarHostState.showSnackbar(
                            message =
                                if (items.size == 1) {
                                    context.getString(R.string.player_removed_song_from_queue, items.first().track.title)
                                } else {
                                    context.getString(R.string.player_removed_n_songs_from_queue, items.size)
                                },
                            actionLabel = context.getString(R.string.player_undo),
                            duration = SnackbarDuration.Short,
                        )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        viewModel.onRestoreQueueItems(items)
                    }
                }
        }
    }
    val onRemoveWithUndo: (QueueTrack) -> Unit = { item -> onRemoveMultipleWithUndo(listOf(item)) }

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
    // Lyrics have no screen yet; the button keeps its place in every bar until they do.
    val onShowLyrics = {}

    BottomSheet(
        state = state,
        backgroundColor = Color.Unspecified,
        modifier = modifier,
        onCollapsedContentClick = openQueue,
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
        val mutableQueue =
            remember {
                mutableStateListOf<QueueTrack>().apply {
                    addAll(queue)
                }
            }
        val queueDurationMs = remember(queue) { queue.sumOf { it.track.durationMs } }

        val headerItems = 1
        val lazyListState = rememberLazyListState()
        var dragInfo by remember { mutableStateOf<QueueDragInfo?>(null) }

        val currentPlayingKey =
            remember(uiState.currentQueueIndex, queue) {
                queue.getOrNull(uiState.currentQueueIndex)?.entry?.key
            }
        // A reorder moves items in the player's own order, which is the order shown only while not shuffling.
        val canReorder = !locked && !playback.shuffleEnabled

        val reorderableState =
            rememberReorderableLazyListState(
                lazyListState = lazyListState,
                scrollThresholdPadding =
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom)
                        .add(WindowInsets(bottom = QueueItemHeight))
                        .asPaddingValues(),
            ) onMove@{ from, to ->
                val fromQueueIndex = from.index - headerItems
                val toQueueIndex = to.index - headerItems
                if (
                    fromQueueIndex !in mutableQueue.indices ||
                    toQueueIndex !in mutableQueue.indices
                ) {
                    return@onMove
                }

                val draggedItemKey = dragInfo?.draggedItemKey ?: mutableQueue[fromQueueIndex].entry.key
                val actualFromQueueIndex = mutableQueue.indexOfFirst { it.entry.key == draggedItemKey }
                if (actualFromQueueIndex == -1) return@onMove

                mutableQueue.add(toQueueIndex, mutableQueue.removeAt(actualFromQueueIndex))
                dragInfo =
                    QueueDragInfo(
                        draggedItemKey = draggedItemKey,
                        destination =
                            if (toQueueIndex == 0) {
                                QueueDragDestination.Start
                            } else {
                                QueueDragDestination.After(itemKey = mutableQueue[toQueueIndex - 1].entry.key)
                            },
                    )
            }

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
                mutableQueue.clear()
                mutableQueue.addAll(queue)
            }
        }

        LaunchedEffect(
            state.isCollapsed,
            scrollToCurrentRequested,
            currentPlayingKey,
            reorderableState.isAnyItemDragging,
        ) {
            if (
                !state.isCollapsed &&
                scrollToCurrentRequested &&
                currentPlayingKey != null &&
                !reorderableState.isAnyItemDragging
            ) {
                val indexInMutableList = mutableQueue.indexOfFirst { it.entry.key == currentPlayingKey }
                if (indexInMutableList != -1) {
                    lazyListState.scrollToItem(indexInMutableList + headerItems)
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
                    locked = locked,
                    songCount = queue.size,
                    queueDurationMs = queueDurationMs,
                    backgroundColor = backgroundColor,
                    onBackgroundColor = onBackgroundColor,
                    onToggleFavorite = viewModel::onToggleFavorite,
                    onMenuClick = { currentTrack?.let(onMenuClick) },
                    onClearQueueClick = {
                        val itemsToRemove =
                            if (uiState.currentQueueIndex in queue.indices) {
                                queue.filterIndexed { index, _ -> index != uiState.currentQueueIndex }
                            } else {
                                emptyList()
                            }

                        if (itemsToRemove.isNotEmpty()) {
                            onRemoveMultipleWithUndo(itemsToRemove)
                            clearSelection()
                        }
                    },
                    onRepeatClick = viewModel::onCycleRepeatMode,
                    onShuffleClick = viewModel::onToggleShuffle,
                    onLockClick = { locked = !locked },
                )

                LazyColumn(
                    state = lazyListState,
                    contentPadding =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            .add(
                                WindowInsets(
                                    bottom = QueueItemHeight + if (selection) 88.dp else 8.dp,
                                ),
                            ).asPaddingValues(),
                    modifier =
                        Modifier
                            .weight(1f)
                            .nestedScroll(state.preUpPostDownNestedScrollConnection),
                ) {
                    item(
                        key = "queue_selection_spacer",
                        contentType = "queue_selection_spacer",
                    ) {
                        Spacer(
                            modifier =
                                Modifier
                                    .animateContentSize()
                                    .height(if (selection) 48.dp else 0.dp),
                        )
                    }

                    items(
                        items = mutableQueue,
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
                                QueueTrackItem(
                                    track = item.track,
                                    isSelected = selection && item in selectedItems,
                                    isActive = isActive,
                                    isPlaying = playback.isPlaying && isActive,
                                    trailingContent = {
                                        IconButton(onClick = { onMenuClick(item.track) }) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
                                        if (canReorder) {
                                            IconButton(
                                                onClick = { },
                                                modifier = Modifier.draggableHandle(),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.drag_handle),
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(backgroundColor)
                                            .combinedClickable(
                                                onClick = {
                                                    if (selection) {
                                                        if (currentItem in selectedItems) {
                                                            selectedItems.remove(currentItem)
                                                            if (selectedItems.isEmpty()) {
                                                                selection = false
                                                            }
                                                        } else {
                                                            selectedItems.add(currentItem)
                                                        }
                                                    } else if (isActive) {
                                                        viewModel.onTogglePlayPause()
                                                    } else {
                                                        viewModel.onPlayQueueItem(currentItem)
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    selection = true
                                                    selectedItems.clear()
                                                    selectedItems.add(currentItem)
                                                },
                                            ),
                                )
                            }

                            if (locked) {
                                content()
                            } else {
                                SwipeToDismissBox(
                                    state = dismissBoxState,
                                    backgroundContent = {},
                                ) {
                                    content()
                                }
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
                            .padding(
                                bottom = (if (selection) QueueItemHeight * 2 + 16.dp else QueueItemHeight) + bottomInset,
                            ).align(Alignment.BottomCenter),
                )

                AnimatedVisibility(
                    visible = selection,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = QueueItemHeight + bottomInset),
                ) {
                    QueueSelectionFloatingToolbar(
                        allSelected = selectedItems.size == mutableQueue.size,
                        onClose = ::clearSelection,
                        onToggleSelectAll = {
                            if (selectedItems.size == mutableQueue.size) {
                                clearSelection()
                            } else {
                                selectedItems.clear()
                                selectedItems.addAll(mutableQueue)
                            }
                        },
                        onDelete = {
                            onRemoveMultipleWithUndo(selectedItems.toList())
                            clearSelection()
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
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
