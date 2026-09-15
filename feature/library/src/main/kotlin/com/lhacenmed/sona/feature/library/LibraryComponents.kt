package com.lhacenmed.sona.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.designsystem.component.CookieShape
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.shimmer
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.sort.SortSheet
import com.lhacenmed.sona.feature.library.sort.sortAction
import kotlin.math.roundToInt

/**
 * Shared building blocks for the library's list/detail screens, kept small and private-ish to
 * this module so every screen feels like a sibling.
 */

/** The size of a loading list's cookie: Auxio's `size_fast_scroll_popup`. */
private val LoadingCookieSize = 96.dp

/**
 * The shell every library list screen renders inside. It keeps the screen's root layout shape
 * identical across all three states, so nothing reflows as data arrives.
 *
 * [LibraryContent.Loading] draws a placeholder rather than either an empty-library message (which
 * would be a lie) or blank space (which reads as a broken screen, and was the "empty for a second,
 * then everything appears at once" the library used to show on launch). The placeholder is centred
 * in the space the list will fill, and takes up none of its layout, so the real list simply replaces
 * it. [loadingIcon] is what the placeholder shows: the icon of what the list holds.
 */
@Composable
internal fun <T> LibraryListContent(
    content: LibraryContent<T>,
    hasPermission: Boolean,
    isScanning: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    loadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    body: @Composable (List<T>) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            content is LibraryContent.Loading ->
                LoadingListPlaceholder(icon = loadingIcon)
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
    loadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onReorder: ((List<T>) -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    LibraryListContent(
        content = content,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        loadingIcon = loadingIcon,
        modifier = modifier,
    ) { items ->
        if (onReorder != null) {
            ReorderableColumn(items = items, key = key, onReorder = onReorder, row = row)
            return@LibraryListContent
        }
        KeepAtTopWhenRowsChange(listState = listState, rows = items)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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

/**
 * Keeps a list that was at its top at its top when its rows change.
 *
 * A keyed lazy list follows its first visible row wherever that row moves. Mid-list that is right - it
 * is what keeps the user's place - but at the top there is no place being kept, and following the row
 * is wrong: re-sorting would scroll to wherever the old first row landed instead of showing the new one.
 *
 * Where the list stood is read as the new rows arrive, before they are measured, and the request is
 * made once per change of rows rather than on every recomposition, so a list scrolled away from its
 * top is never pulled back to it.
 */
@Composable
private fun KeepAtTopWhenRowsChange(listState: LazyListState, rows: List<*>) {
    val wasAtTop = remember(rows) {
        Snapshot.withoutReadObservation {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    DisposableEffect(rows) {
        if (wasAtTop) listState.requestScrollToItem(0)
        onDispose {}
    }
}

private const val NO_DRAG = -1

/**
 * The drag gesture for a row's handle, inside a list that can be reordered - null in every other list.
 *
 * Provided per row by [ReorderableColumn] rather than passed through [LibraryList]'s `row`, which every
 * list shares and only a reorderable one has any use for.
 */
private val LocalDragHandle = compositionLocalOf<Modifier?> { null }

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
    KeepAtTopWhenRowsChange(listState = listState, rows = items)
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
                // The row draws the handle itself, beside its menu button, and gives it this gesture.
                CompositionLocalProvider(
                    LocalDragHandle provides Modifier.pointerInput(items) {
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
                ) {
                    row(item)
                }
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
            .padding(horizontal = SonaComponentStyle.ContentHorizontalPadding, vertical = 12.dp),
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
 * Auxio's empty-list cookie - the six-sided expressive shape with [icon] inside it - centred and
 * shimmering while the list loads.
 *
 * The loading window should normally be a frame or two (the library is already in memory by then), so
 * the cookie carries no message: text would only flash. On a genuinely slow first read, the shimmer
 * is what says the list is on its way.
 */
@Composable
private fun LoadingListPlaceholder(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(LoadingCookieSize)
                .shimmer()
                .background(MaterialTheme.colorScheme.surfaceVariant, CookieShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val selection = rememberSelectionState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf<String?>(null) }
    var isSortSheetOpen by remember { mutableStateOf(false) }
    val sort = viewModel.sort

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
                if (sort != null) add(sortAction { isSortSheetOpen = true })
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
            loadingIcon = SonaIcons.Song,
        ) { track ->
            TrackRow(
                track = track,
                isCurrent = { track.id == currentTrackId },
                isPlaying = { isPlaying },
                selection = selection,
                onClick = { viewModel.onTrackClick(track) },
            )
        }
    }

    if (isSortSheetOpen && sort != null) {
        SortSheet(sort = sort, onDismiss = { isSortSheetOpen = false })
    }
}

/** How strongly a selected row is tinted with the primary colour: Auxio's `sel_item_activated_bg`. */
private const val SELECTED_ROW_TINT_ALPHA = 0.12f

/** How long that tint takes to fade in and out: Auxio's `anim_fade_enter_duration` and exit duration. */
private const val SELECTED_ROW_FADE_IN_MILLIS = 200
private const val SELECTED_ROW_FADE_OUT_MILLIS = 100

/** The touch target a drag handle is centred in: Auxio's `size_touchable_small`. */
private val DragHandleTouchSize = 48.dp

/**
 * A track row, laid out as Auxio's `item_song`: cover art, title over "artist - album", and the row's
 * own overflow button - with a drag handle beside that button in a list that can be reordered.
 *
 * [isCurrent] and [isPlaying] are lambdas, not values, on purpose. Passed as `Boolean`s, every row in
 * the list recomposes whenever the playing track changes, because each row's parameters changed.
 * Read inside the row's own composition, only the row that was marked and the row that now is do any
 * work.
 *
 * The playing track is shown by its cover and its title alone - see [SonaCoverArt]. Nothing paints
 * the row behind them, so the one tint a row can have still means "selected".
 *
 * The overflow button stays through a selection: the row keeps one shape whatever state it is in.
 */
@Composable
internal fun TrackRow(
    track: Track,
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    selection: SelectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    val isSelected = selection.isSelected(track.id)
    // The fade is animated rather than the colour, and read only when drawing: an animated colour would
    // trail behind every theme transition, and reading it here would recompose the row every frame.
    val selectedFraction = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isSelected) SELECTED_ROW_FADE_IN_MILLIS else SELECTED_ROW_FADE_OUT_MILLIS,
        ),
        label = "trackRowSelection",
    )
    val selectedTint = MaterialTheme.colorScheme.primary
    val dragHandle = LocalDragHandle.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawRect(selectedTint.copy(alpha = SELECTED_ROW_TINT_ALPHA * selectedFraction.value))
            }
            .selectableRow(selection, track.id, onClick)
            .padding(
                start = SonaComponentStyle.ContentHorizontalPadding,
                top = 12.dp,
                // The overflow glyph, not its 48dp touch target, ends on the keyline: the target
                // holds the glyph 12dp in from its edge.
                end = SonaComponentStyle.ContentHorizontalPadding - 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SonaCoverArt(
            coverArtUri = track.coverArtUri,
            contentDescription = null,
            isCurrent = current,
            isPlaying = isPlaying(),
            isSelected = isSelected,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 12.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (current) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist} - ${track.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (dragHandle != null) {
            Box(
                modifier = Modifier
                    .size(DragHandleTouchSize)
                    .then(dragHandle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SonaIconButton(
            // Opens nothing yet. It takes its place now because the row's shape is part of the
            // list's: adding it later would move the title and the cover of every row.
            onClick = {},
            icon = Icons.Filled.MoreHoriz,
            contentDescription = "More options",
        )
    }
}

/** Narrows a loaded list, leaving "still loading" alone so a search cannot look like an empty library. */
internal fun <T> LibraryContent<T>.filterItems(predicate: (T) -> Boolean): LibraryContent<T> =
    when (this) {
        is LibraryContent.Loading -> this
        is LibraryContent.Ready -> LibraryContent.Ready(items.filter(predicate))
    }
