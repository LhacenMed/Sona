package com.lhacenmed.sona.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.model.Track
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared building blocks for the library's list/detail screens, kept small and private-ish to
 * this module so every screen feels like a sibling.
 */

/** How many placeholder rows a loading list draws. Enough to fill a phone screen, no more. */
private const val PLACEHOLDER_ROW_COUNT = 12

/**
 * The shell every library list screen renders inside. It keeps the screen's root layout shape
 * identical across all three states, so nothing reflows as data arrives.
 *
 * [LibraryContent.Loading] draws a placeholder list rather than either an empty-library message
 * (which would be a lie) or blank space (which reads as a broken screen, and was the "empty for a
 * second, then everything appears at once" the library used to show on launch). Because the
 * placeholder rows are the same height as real ones, the real list replaces them in place instead of
 * pushing the screen around.
 */
@Composable
internal fun <T> LibraryListContent(
    content: LibraryContent<T>,
    hasPermission: Boolean,
    isScanning: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    body: @Composable (List<T>) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            content is LibraryContent.Loading -> LoadingListPlaceholder()
            content is LibraryContent.Ready && content.items.isEmpty() -> {
                val (title, message) = emptyLibraryStateContent(
                    hasPermission = hasPermission,
                    isScanning = isScanning,
                    emptyTitle = emptyTitle,
                    emptyMessage = emptyMessage,
                )
                EmptyLibraryState(title = title, message = message)
            }
            content is LibraryContent.Ready -> body(content.items)
        }
    }
}

/**
 * [LibraryListContent] plus the `LazyColumn` every list screen was writing out by hand.
 *
 * Centralising it is what makes stable [key]s and a [contentType] non-optional. Without a key, Lazy
 * layouts fall back to item *position*, so when the library changes every row is treated as a
 * different row: scroll position jumps, and nothing can be reused. With one, a rescan that reorders
 * or inserts a few tracks moves the existing rows instead of rebuilding the list.
 */
@Composable
internal fun <T> LibraryList(
    content: LibraryContent<T>,
    hasPermission: Boolean,
    isScanning: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    onReorder: ((List<T>) -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    LibraryListContent(
        content = content,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        modifier = modifier,
    ) { items ->
        if (onReorder != null) {
            ReorderableColumn(items = items, key = key, onReorder = onReorder, row = row)
            return@LibraryListContent
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = items,
                key = key,
                // Every row in these lists is the same composable shape, so telling Compose that
                // lets it reuse a scrolled-off row's slot table wholesale instead of rebuilding it.
                contentType = { LIST_ROW_CONTENT_TYPE },
            ) { item -> row(item) }
        }
    }
}

private const val LIST_ROW_CONTENT_TYPE = "libraryRow"

private const val NO_DRAG = -1

/**
 * A list whose rows can be dragged into a new order.
 *
 * Dragging starts from a handle rather than a long press, because long press already starts a
 * selection - one gesture cannot mean both, and a handle is what the reference app uses too.
 *
 * The order shown while dragging is derived rather than stored: the row is taken out of the list and
 * put back wherever the finger has reached, so an abandoned drag leaves nothing half-moved. Only the
 * order the finger let go of is written, once, on drop.
 *
 * Rows in these lists are all the same height, which is what lets the target be arithmetic rather
 * than a hit test against every visible row.
 */
@Composable
private fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    row: @Composable (T) -> Unit,
) {
    val listState = rememberLazyListState()
    var draggedIndex by remember(items) { mutableIntStateOf(NO_DRAG) }
    var dragOffsetPx by remember(items) { mutableFloatStateOf(0f) }

    val rowHeightPx = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
    val targetIndex = if (draggedIndex == NO_DRAG || rowHeightPx == 0) {
        NO_DRAG
    } else {
        (draggedIndex + (dragOffsetPx / rowHeightPx).roundToInt()).coerceIn(0, items.lastIndex)
    }
    val ordered = if (targetIndex == NO_DRAG) {
        items
    } else {
        items.toMutableList().apply { add(targetIndex, removeAt(draggedIndex)) }
    }

    // The gesture callbacks are created once per `items` and would otherwise close over the values
    // that existed when the drag began - a target of "no drag" and the untouched order - so the drop
    // would compare them, find nothing had moved, and write nothing at all. These read the latest.
    val latestTarget by rememberUpdatedState(targetIndex)
    val latestOrder by rememberUpdatedState(ordered)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(
            items = ordered,
            key = { _, item -> key(item) },
            contentType = { _, _ -> LIST_ROW_CONTENT_TYPE },
        ) { index, item ->
            val isDragging = targetIndex != NO_DRAG && index == targetIndex
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        // The row follows the finger, less the distance it has already covered by
                        // changing places with its neighbours.
                        translationY = if (isDragging) {
                            dragOffsetPx - (targetIndex - draggedIndex) * rowHeightPx
                        } else {
                            0f
                        }
                    },
            ) {
                row(item)
                // Drawn over the row rather than beside it, so it sits on whatever background the
                // row has - selected, playing or plain - instead of cutting a strip out of it.
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(horizontal = 16.dp)
                        .pointerInput(items) {
                            detectDragGestures(
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffsetPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetPx += dragAmount.y
                                },
                                onDragEnd = {
                                    if (latestTarget != NO_DRAG && latestTarget != draggedIndex) {
                                        onReorder(latestOrder)
                                    }
                                    draggedIndex = NO_DRAG
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = {
                                    draggedIndex = NO_DRAG
                                    dragOffsetPx = 0f
                                },
                            )
                        },
                )
            }
        }
    }
}

/**
 * Tap and long-press behaviour for any row that can be selected.
 *
 * Long-press starts a selection; once one is running an ordinary tap adds to it instead of opening
 * anything, which is what stops a stray tap from navigating away mid-selection. Every selectable
 * list in the app goes through this, so the gesture cannot drift between them.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.selectableRow(
    selection: SelectionState,
    selectionKey: Any,
    onClick: () -> Unit,
): Modifier = combinedClickable(
    onClick = { if (selection.isActive) selection.toggle(selectionKey) else onClick() },
    onLongClick = { selection.toggle(selectionKey) },
)

/** The two-line row shape shared by the artists, albums, genres and folders tabs. */
@Composable
internal fun LibraryEntityRow(
    title: String,
    subtitle: String,
    // Null for a row that stands for something other than a real entity - the derived lists on the
    // playlists screen - so it can be opened but never gathered into a selection meant for playlists.
    selection: SelectionState?,
    selectionKey: Any,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selection?.isSelected(selectionKey) == true) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .then(
                if (selection == null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.selectableRow(selection, selectionKey, onClick)
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A quietly pulsing set of row-shaped blocks.
 *
 * Deliberately low-contrast and slow: the loading window should normally be a frame or two (the
 * library is already in memory by then), so this must never read as a "loading spinner" moment. It
 * only becomes visible at all on a genuinely slow first read.
 */
@Composable
private fun LoadingListPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "libraryPlaceholder")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "libraryPlaceholderAlpha",
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant

    // A plain Column, not a LazyColumn: the count is fixed and small, and a lazy container here
    // would allocate scroll state that is thrown away as soon as the real list arrives.
    Column(modifier = modifier.fillMaxSize()) {
        repeat(PLACEHOLDER_ROW_COUNT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlaceholderBar(widthFraction = 0.55f, alpha = alpha, color = color)
                PlaceholderBar(widthFraction = 0.32f, alpha = alpha * 0.7f, color = color)
            }
        }
    }
}

@Composable
private fun PlaceholderBar(
    widthFraction: Float,
    alpha: Float,
    color: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(12.dp)
            .clip(RoundedCornerShape(4.dp))
            // drawBehind, not background(): the colour changes every animation frame, and drawing
            // it in the draw phase skips recomposition entirely.
            .drawBehind { drawRect(color = color.copy(alpha = alpha)) },
    )
}

/**
 * Resolves what an empty list screen should say. A list is empty for one of three genuinely
 * different reasons - conflating them (as a plain "no items" message would) is what caused Sona to
 * flash a "grant permission" message even when permission was already granted: the real reason was
 * simply that the scan hadn't populated the database yet.
 */
internal fun emptyLibraryStateContent(
    hasPermission: Boolean,
    isScanning: Boolean,
    emptyTitle: String,
    emptyMessage: String,
): Pair<String, String> = when {
    !hasPermission -> "Permission needed" to "Sona needs access to your audio files to show your library."
    isScanning -> "Scanning your library…" to "This only takes a moment."
    else -> emptyTitle to emptyMessage
}

@Composable
internal fun EmptyLibraryState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The whole body of a detail screen: a header, then that entity's tracks.
 *
 * The album, artist, genre and folder detail screens differ only in their heading and their query,
 * so they share this. It also means they inherit the list screens' loading state - previously they
 * started from a non-null empty state and so briefly rendered "no tracks" over a list that existed.
 */
@Composable
internal fun TrackListDetail(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    viewModel: TrackListDetailViewModel,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    extraActions: List<TopBarAction> = emptyList(),
    onReorder: ((List<Track>) -> Unit)? = null,
    onRemoveSelected: ((Set<Any>) -> Unit)? = null,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle()
    val selection = rememberSelectionState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(M3U_MIME_TYPE),
    ) { uri ->
        if (uri != null) viewModel.exportTo { context.contentResolver.openOutputStream(uri) }
    }

    // Filtering the list rather than re-querying: the rows are already here, and a playlist's
    // order has to survive being searched.
    val visibleTracks = tracks.filterItems { track ->
        val query = searchQuery.orEmpty()
        query.isBlank() ||
            track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        SonaTopAppBar(
            title = title,
            subtitle = subtitle,
            onNavigateBack = onBack,
            actions = buildList {
                add(
                    TopBarAction(label = "Search", icon = Icons.Filled.Search) { searchQuery = "" },
                )
                add(SortPlaceholderAction)
                addAll(extraActions)
                add(
                    TopBarAction(label = "Export playlist", icon = Icons.Filled.FileUpload) {
                        exportLauncher.launch("$title.m3u")
                    },
                )
            },
            search = searchQuery?.let { query ->
                TopBarSearch(
                    query = query,
                    onQueryChange = { searchQuery = it },
                    onClose = { searchQuery = null },
                )
            },
            selection = selection.toTopBarSelection(
                actions = buildList {
                    add(
                        TopBarAction(label = "Play", icon = Icons.Filled.PlayArrow) {
                            viewModel.playSelection(selection.selectedKeys)
                            selection.clear()
                        },
                    )
                    // Only a real playlist has membership to remove from.
                    if (onRemoveSelected != null) {
                        add(
                            TopBarAction(
                                label = "Remove from playlist",
                                icon = Icons.Filled.RemoveCircleOutline,
                            ) {
                                onRemoveSelected(selection.selectedKeys)
                                selection.clear()
                            },
                        )
                    }
                    add(
                        TopBarAction(label = "Select all", icon = Icons.Filled.SelectAll) {
                            selection.selectAll(viewModel.selectableKeys())
                        },
                    )
                },
            ),
        )
        LibraryList(
            content = visibleTracks,
            // A detail screen is only reachable from a library that already loaded, so neither the
            // permission nor the scanning explanation can apply here.
            hasPermission = true,
            isScanning = false,
            emptyTitle = "No tracks found",
            emptyMessage = emptyMessage,
            key = { it.id },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Handles appear with the context bar: dragging is something done to a selection, so
            // an ordinary tap-to-play list is never cluttered by them. A search has reordered the
            // list already, so a drop would write an order the user cannot see.
            onReorder = onReorder.takeIf { searchQuery.isNullOrBlank() && selection.isActive },
        ) { track ->
            TrackRow(
                track = track,
                isPlaying = { track.id == currentTrackId },
                selection = selection,
                onClick = { viewModel.onTrackClick(track) },
            )
        }
    }
}

/**
 * A track row.
 *
 * [isPlaying] is a lambda, not a value, on purpose. Passed as a `Boolean`, every row in the list
 * recomposes whenever the playing track changes, because each row's parameters changed. Passed as a
 * lambda read inside the row's own composition, only the row that was highlighted and the row that
 * now is do any work.
 */
@Composable
internal fun TrackRow(
    track: Track,
    isPlaying: () -> Boolean,
    selection: SelectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = isPlaying()
    val background = when {
        // Selection outranks playback, and gets its own colour: were both the same, a selected row
        // and the playing row would be indistinguishable exactly when the difference matters.
        selection.isSelected(track.id) -> MaterialTheme.colorScheme.primaryContainer
        playing -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .selectableRow(selection, track.id, onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "${track.artist} · ${formatTrackDuration(track.durationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

/**
 * The sort button, which does not sort yet.
 *
 * Present because the bar's action set is part of the screen's shape: adding it later would move
 * every other action along, so it takes its place now and gains its menu when sorting arrives.
 */
internal val SortPlaceholderAction = TopBarAction(
    label = "Sort",
    icon = Icons.AutoMirrored.Filled.Sort,
    onClick = {},
)

/** Narrows a loaded list, leaving "still loading" alone so a search cannot look like an empty library. */
internal fun <T> LibraryContent<T>.filterItems(predicate: (T) -> Boolean): LibraryContent<T> =
    when (this) {
        is LibraryContent.Loading -> this
        is LibraryContent.Ready -> LibraryContent.Ready(items.filter(predicate))
    }
