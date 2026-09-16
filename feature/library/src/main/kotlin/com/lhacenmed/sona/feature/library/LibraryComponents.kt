package com.lhacenmed.sona.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.CookieShape
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.shimmer
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.operation.ConfirmedOperationDialog
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.options.TrackOptionsContext
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.selection.SelectionOptionsHost
import com.lhacenmed.sona.feature.library.selection.toLibraryTopBarSelection
import com.lhacenmed.sona.feature.library.sort.SortSheet
import com.lhacenmed.sona.feature.library.sort.sortAction
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
            // The same list state as the plain list below, so the rows keep their place when handles
            // appear and again when they go: a list state belongs to the list, not to one of its modes.
            ReorderableColumn(
                items = items,
                key = key,
                listState = listState,
                onReorder = onReorder,
                row = row,
            )
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

/** How close to an edge a drag starts scrolling the list: one row's height, as the queue uses. */
private val ReorderAutoScrollThreshold = 72.dp

/**
 * The drag gesture for a row's handle, inside a list that can be reordered - null in every other list.
 *
 * Provided per row by [ReorderableColumn] rather than passed through [LibraryList]'s `row`, which every
 * list shares and only a reorderable one has any use for.
 */
internal val LocalDragHandle = compositionLocalOf<Modifier?> { null }

/**
 * A list whose rows can be dragged into a new order, on the same drag system as the player's queue.
 *
 * Dragging starts from a handle rather than a long press, because long press already starts a
 * selection - one gesture cannot mean both, and a handle is what the reference app uses too.
 *
 * The order on screen is a copy that the drag edits as the finger moves, so rows change places under
 * the finger and nothing waits on the database. The order the finger let go of is written once, on
 * drop, and only when it actually differs - until then the stored order is untouched, so an abandoned
 * drag leaves nothing half-moved. With no drag running the copy simply follows the list it mirrors.
 */
@Composable
private fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    listState: LazyListState,
    onReorder: (List<T>) -> Unit,
    row: @Composable (T) -> Unit,
) {
    KeepAtTopWhenRowsChange(listState = listState, rows = items)
    val orderedRows = remember { mutableStateListOf<T>().apply { addAll(items) } }
    var hasDropToWrite by remember { mutableStateOf(false) }
    var writtenOrder by remember { mutableStateOf<List<Any>?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        scrollThresholdPadding = PaddingValues(vertical = ReorderAutoScrollThreshold),
    ) { from, to ->
        if (from.index in orderedRows.indices && to.index in orderedRows.indices) {
            orderedRows.add(to.index, orderedRows.removeAt(from.index))
            hasDropToWrite = true
        }
    }

    // Written once the finger is off the row, never while it moves. Anything else that changes the
    // list - rows arriving, a re-sort - refills the copy instead.
    LaunchedEffect(items, reorderableState.isAnyItemDragging) {
        if (reorderableState.isAnyItemDragging) return@LaunchedEffect
        val itemKeys = items.map(key)
        if (hasDropToWrite) {
            hasDropToWrite = false
            val droppedKeys = orderedRows.map(key)
            if (droppedKeys != itemKeys) {
                writtenOrder = droppedKeys
                onReorder(orderedRows.toList())
                return@LaunchedEffect
            }
        }
        // A drop is written in two parts - the arranged order, and the switch to it - so the list can
        // arrive still in the order it was sorted by. The copy keeps what the finger left until the
        // list catches up, and gives way at once if the rows themselves changed while it waited.
        val awaited = writtenOrder
        if (awaited != null && itemKeys != awaited && itemKeys.toSet() == awaited.toSet()) {
            return@LaunchedEffect
        }
        writtenOrder = null
        Snapshot.withMutableSnapshot {
            orderedRows.clear()
            orderedRows.addAll(items)
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(
            items = orderedRows,
            key = key,
            contentType = { LIST_ROW_CONTENT_TYPE },
        ) { item ->
            ReorderableItem(state = reorderableState, key = key(item)) {
                // The row draws the handle itself, beside its menu button, and gives it this gesture.
                CompositionLocalProvider(LocalDragHandle provides Modifier.draggableHandle()) {
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
 *
 * A row with no [selectionKey] cannot be selected - Auxio's empty collection: a long-press does
 * nothing, and while a selection runs neither does a tap.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.selectableRow(
    selection: SelectionState,
    selectionKey: SelectionKey?,
    onClick: () -> Unit,
): Modifier = combinedClickable(
    onClick = {
        when {
            !selection.isActive -> onClick()
            selectionKey != null -> selection.toggle(selectionKey)
        }
    },
    onLongClick = { selectionKey?.let(selection::toggle) },
)

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
 *
 * The bar's menu is [extraActions], then Export, then [trailingActions]. [removeFromPlaylist] is
 * given only by a real playlist, the one list with membership to remove from; removing asks first and
 * reports how it went.
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
    trailingActions: List<TopBarAction> = emptyList(),
    onReorder: ((List<Track>) -> Unit)? = null,
    removeFromPlaylist: ((trackIds: List<Long>, onFinished: (succeeded: Boolean) -> Unit) -> Unit)? = null,
    trackOptionsContext: TrackOptionsContext = TrackOptionsContext.LIST,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val selection = rememberSelectionState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf<String?>(null) }
    var isSortSheetOpen by remember { mutableStateOf(false) }
    var optionsTarget by remember { mutableStateOf<OptionsTarget.ForTrack?>(null) }
    // The selected tracks waiting on the user to confirm removing them, in the order they were selected.
    var removingTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
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

    SelectionOptionsHost(selection) { openSelectionOptions ->
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
                    addAll(trailingActions)
                },
                search = searchQuery?.let { query ->
                    TopBarSearch(
                        query = query,
                        onQueryChange = { searchQuery = it },
                        onClose = { searchQuery = null },
                    )
                },
                selection = selection.toLibraryTopBarSelection(
                    listKeys = viewModel::selectableKeys,
                    // Only a real playlist has membership to remove from.
                    actions = listOfNotNull(
                        removeFromPlaylist?.let {
                            TopBarAction(label = "Remove from playlist", icon = Icons.Filled.RemoveCircleOutline) {
                                val tracksById = tracks.itemsOrEmpty.associateBy { it.id }
                                removingTracks = selection.selectedKeys.filterIsInstance<SelectionKey.Track>()
                                    .mapNotNull { tracksById[it.trackId] }
                            }
                        },
                    ),
                    onMoreOptions = openSelectionOptions,
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
                    isCurrent = { playback.marks(track) },
                    isPlaying = { playback.isPlaying },
                    selection = selection,
                    onClick = { viewModel.onTrackClick(track) },
                    onOpenOptions = {
                        optionsTarget = OptionsTarget.ForTrack(
                            track = track,
                            context = trackOptionsContext,
                            queueSource = tracks.itemsOrEmpty,
                            queueParent = viewModel.playbackParent,
                        )
                    },
                )
            }
        }
    }

    if (isSortSheetOpen && sort != null) {
        SortSheet(sort = sort, onDismiss = { isSortSheetOpen = false })
    }

    optionsTarget?.let { target ->
        OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
    }

    if (removeFromPlaylist != null && removingTracks.isNotEmpty()) {
        val isSingle = removingTracks.size == 1
        ConfirmedOperationDialog(
            title = if (isSingle) "Remove track" else "Remove ${removingTracks.size} tracks",
            message = "From $title. The files themselves are not deleted.",
            total = pluralCount(removingTracks.size, "track"),
            confirmLabel = "Remove",
            successMessage = if (isSingle) "Track removed" else "${removingTracks.size} tracks removed",
            failureMessage = if (isSingle) "Could not remove track" else "Could not remove tracks",
            onDismiss = { removingTracks = emptyList() },
            operation = { onFinished ->
                // The selection ends once the removal is confirmed; cancelling leaves every row picked.
                selection.clear()
                removeFromPlaylist(removingTracks.map { it.id }, onFinished)
            },
        )
    }
}

/** Narrows a loaded list, leaving "still loading" alone so a search cannot look like an empty library. */
internal fun <T> LibraryContent<T>.filterItems(predicate: (T) -> Boolean): LibraryContent<T> =
    when (this) {
        is LibraryContent.Loading -> this
        is LibraryContent.Ready -> LibraryContent.Ready(items.filter(predicate))
    }
