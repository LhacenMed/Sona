package com.lhacenmed.sona.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.lhacenmed.sona.core.designsystem.component.FastScroller
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.LocalDragHandle
import com.lhacenmed.sona.core.designsystem.component.LocalDragSelection
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.dragSelection
import com.lhacenmed.sona.core.designsystem.component.rememberDragSelection
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.shimmer
import com.lhacenmed.sona.core.designsystem.component.swipe.LocalSwipeActions
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.model.Track
import androidx.compose.material3.HorizontalDivider
import com.lhacenmed.sona.core.designsystem.component.DetailHeader
import com.lhacenmed.sona.core.designsystem.component.DetailScaffold
import com.lhacenmed.sona.core.designsystem.component.DetailSectionHeader
import com.lhacenmed.sona.core.designsystem.component.SonaIconButtonGroup
import com.lhacenmed.sona.core.designsystem.component.TopBarCollapse
import com.lhacenmed.sona.core.designsystem.component.iconButton
import com.lhacenmed.sona.core.designsystem.component.rememberDetailHeaderState
import com.lhacenmed.sona.core.designsystem.theme.LocalIsRounded
import com.lhacenmed.sona.core.designsystem.theme.SquareShape
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.library.operation.RemoveFromPlaylistDialog
import com.lhacenmed.sona.feature.library.options.formatDurationMs
import com.lhacenmed.sona.feature.library.options.OptionsFollowUps
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.options.TrackOptionsContext
import com.lhacenmed.sona.feature.library.options.actions
import com.lhacenmed.sona.feature.library.options.disabledActions
import com.lhacenmed.sona.feature.library.options.rememberOptionsActions
import com.lhacenmed.sona.feature.library.options.rememberQueueSwipeActions
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.selection.SelectionOptionsHost
import com.lhacenmed.sona.feature.library.selection.selectionKeyOf
import com.lhacenmed.sona.feature.library.selection.toLibraryTopBarSelection
import com.lhacenmed.sona.feature.library.sort.SortSheet
import com.lhacenmed.sona.feature.library.sort.sortAction
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
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
 * it. [loadingIcon] is what the placeholder shows: the icon of what the list holds. [emptyAction] is
 * what an empty list offers to fill it, if anything.
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
    emptyAction: EmptyStateAction? = null,
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
                EmptyLibraryState(title = title, message = message, action = emptyAction)
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
 *
 * Every list has the fast scroller; one given [sectionOf] - the section its sort puts a row in - also
 * names that section in the scroller's popup. [onFastScrollingChange] hears when its thumb is dragged.
 *
 * Its rows join [selection], and a long press drags across them - see [DragSelection].
 */
@Composable
internal fun <T> LibraryList(
    content: LibraryContent<T>,
    selection: SelectionState,
    hasPermission: Boolean,
    isScanning: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    key: (T) -> Any,
    loadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onReorder: ((List<T>) -> Unit)? = null,
    sectionOf: ((T) -> String?)? = null,
    onFastScrollingChange: (Boolean) -> Unit = {},
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
        FastScroller(
            listState = listState,
            modifier = Modifier.fillMaxSize(),
            sectionAt = sectionOf?.let { section -> { index -> items.getOrNull(index)?.let(section) } },
            onFastScrollingChange = onFastScrollingChange,
        ) {
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
                return@FastScroller
            }
            KeepAtTopWhenRowsChange(listState = listState, rows = items)
            val selectableKeys = remember(items) { items.mapNotNull(::selectionKeyOf) }
            val dragSelection = rememberDragSelection(selection, listState, selectableKeys)
            CompositionLocalProvider(LocalDragSelection provides dragSelection) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .dragSelection(dragSelection),
                    contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                ) {
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
 * A list whose rows can be dragged into a new order, on the same drag system as the player's queue.
 *
 * Dragging starts from a handle rather than a long press, because long press already starts a
 * selection - one gesture cannot mean both, and a handle is what the reference app uses too.
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
    val reorderableRows = rememberReorderableRows(items, key, listState, onReorder)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
    ) {
        reorderableRows(reorderableRows, key, row)
    }
}

/** [items] as a drag leaves them, and the drag that moves them - see [rememberReorderableRows]. */
internal class ReorderableRows<T>(
    val rows: List<T>,
    val state: ReorderableLazyListState,
)

/**
 * [items], draggable into a new order within [listState]'s list - whatever else that list holds.
 *
 * The order on screen is a copy that the drag edits as the finger moves, so rows change places under
 * the finger and nothing waits on the database. The order the finger let go of is written once, on
 * drop, and only when it actually differs - until then the stored order is untouched, so an abandoned
 * drag leaves nothing half-moved. With no drag running the copy simply follows the list it mirrors.
 *
 * Rows are told apart by [key] rather than by where they sit in the list, so a list that has other
 * sections above these rows moves the right ones.
 */
@Composable
internal fun <T> rememberReorderableRows(
    items: List<T>,
    key: (T) -> Any,
    listState: LazyListState,
    onReorder: (List<T>) -> Unit,
): ReorderableRows<T> {
    val orderedRows = remember { mutableStateListOf<T>().apply { addAll(items) } }
    var hasDropToWrite by remember { mutableStateOf(false) }
    var writtenOrder by remember { mutableStateOf<List<Any>?>(null) }

    val bottomContentPadding = LocalBottomContentPadding.current
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        // The list's bottom lies under the mini player, so the edge a drag scrolls from is its top.
        scrollThresholdPadding = PaddingValues(
            top = ReorderAutoScrollThreshold,
            bottom = ReorderAutoScrollThreshold + bottomContentPadding,
        ),
    ) { from, to ->
        val fromIndex = orderedRows.indexOfFirst { key(it) == from.key }
        val toIndex = orderedRows.indexOfFirst { key(it) == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            orderedRows.add(toIndex, orderedRows.removeAt(fromIndex))
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

    return remember(reorderableState) { ReorderableRows(orderedRows, reorderableState) }
}

/** [reorderableRows]' rows, each draggable by the handle its row draws. */
internal fun <T> LazyListScope.reorderableRows(
    reorderableRows: ReorderableRows<T>,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    items(
        items = reorderableRows.rows,
        key = key,
        contentType = { LIST_ROW_CONTENT_TYPE },
    ) { item ->
        ReorderableItem(state = reorderableRows.state, key = key(item)) {
            // The row draws the handle itself, beside its menu button, and gives it this gesture.
            CompositionLocalProvider(LocalDragHandle provides Modifier.draggableHandle()) {
                row(item)
            }
        }
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
                .background(MaterialTheme.colorScheme.surfaceVariant, if (LocalIsRounded.current) CookieShape else SquareShape),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EmptyLibraryState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: EmptyStateAction? = null,
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
        if (action != null) {
            Button(
                onClick = action.onClick,
                shapes = buttonPressShapes(),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Icon(action.icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(action.label)
            }
        }
    }
}

/** A button an empty list offers, to fill it - the playlists' "Create playlist". */
internal class EmptyStateAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * What a detail screen's header says about its collection - see [DetailHeader]. [cover] is drawn at
 * the header's size; [subhead] is left out where there is nothing to say, as Auxio hides its line.
 */
internal class DetailHeaderContent(
    val type: String,
    val subhead: String?,
    val info: String,
    val cover: @Composable () -> Unit,
)

/** "12 tracks • 45:12" - a list's track count and how long it all plays for. */
internal fun trackCountAndDuration(tracks: List<Track>): String =
    listOf(trackCountLabel(tracks.size), formatDurationMs(tracks.sumOf { it.durationMs })).joinToString(DETAIL_INFO_SEPARATOR)

/** What separates the parts of a header's info line - Auxio's `fmt_two`. */
internal const val DETAIL_INFO_SEPARATOR = " • "

/**
 * The whole body of a detail screen: a header that collapses into the bar, then the collection's
 * sections, then its tracks - Auxio's detail screen, the album, artist, genre, playlist and folder
 * screens alike, and the listening histories.
 *
 * The header ([DetailScaffold]) is the collection's cover, kind, name, [header]'s lines about it, and
 * Play and Shuffle, which move up into the bar as the header collapses. Below it come
 * [TrackListDetailViewModel.sections] - an artist's albums, a genre's artists - and last the tracks,
 * whose heading carries the sort button, as Auxio's songs section does. [groupsByDisc] splits an
 * album's tracks under a heading per disc, once there is more than one.
 *
 * The tracks' heading carries search beside sort. The bar's menu is [collection]'s own actions -
 * exactly what its row's options sheet lists, minus View - carried out the way the sheet carries them
 * out; a screen that closes once its collection is deleted goes back. A list that is no collection of
 * its own - Recent, Most played - offers Export in their place. Searching narrows the tracks alone, in
 * a field in the bar with the results straight under it: the header and the other sections step
 * aside. A [collection] that is a playlist is the one list with membership to remove from: its tracks'
 * sheets and its selection bar both offer removing, which asks first and reports how it went.
 */
@Composable
internal fun TrackListDetail(
    title: String,
    header: DetailHeaderContent,
    onBack: () -> Unit,
    viewModel: TrackListDetailViewModel,
    emptyMessage: String,
    collection: OptionsTarget?,
    modifier: Modifier = Modifier,
    onReorder: ((List<Track>) -> Unit)? = null,
    trackOptionsContext: TrackOptionsContext = TrackOptionsContext.LIST,
    groupsByDisc: Boolean = false,
    trackSubtitle: ((Track) -> String)? = null,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val selection = rememberSelectionState()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val collectionActions = rememberOptionsActions()
    val headerState = rememberDetailHeaderState()
    var searchQuery by remember { mutableStateOf<String?>(null) }
    var isSortSheetOpen by remember { mutableStateOf(false) }
    var optionsTarget by remember { mutableStateOf<OptionsTarget?>(null) }
    // The selected tracks waiting on the user to confirm removing them, in the order they were selected.
    var removingTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val playlist = (collection as? OptionsTarget.ForPlaylist)?.playlist
    val sort = viewModel.sort
    val hasTracks = tracks.itemsOrEmpty.isNotEmpty()

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

    // Handles appear with the context bar: dragging is something done to a selection, so an ordinary
    // tap-to-play list is never cluttered by them. A search has reordered the list already, so a drop
    // would write an order the user cannot see.
    val reorder = onReorder.takeIf { searchQuery.isNullOrBlank() && selection.isActive }
    val reorderableRows = reorder?.let {
        rememberReorderableRows(visibleTracks.itemsOrEmpty, { track -> track.id }, headerState.listState, it)
    }

    val collapse = remember(headerState, hasTracks) {
        TopBarCollapse(
            progress = { headerState.collapse },
            isLifted = { headerState.isLifted },
            play = TopBarAction(label = "Play", icon = SonaIcons.Play, enabled = hasTracks) {
                viewModel.onPlayAll(shuffled = false)
            },
            shuffle = TopBarAction(label = "Shuffle", icon = SonaIcons.Shuffle, enabled = hasTracks) {
                viewModel.onPlayAll(shuffled = true)
            },
        )
    }

    // Every row swipes to play next or join the queue - see [rememberQueueSwipeActions].
    val trackRow: @Composable (Track) -> Unit = { track ->
        CompositionLocalProvider(
            LocalSwipeActions provides rememberQueueSwipeActions(collectionActions, OptionsTarget.ForTrack(track)),
        ) {
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
                        playlist = playlist,
                    )
                },
                subtitle = trackSubtitle?.invoke(track),
            )
        }
    }

    SelectionOptionsHost(selection) { openSelectionOptions ->
        DetailScaffold(
            state = headerState,
            modifier = modifier,
            bar = {
                SonaTopAppBar(
                    title = title,
                    onNavigateBack = onBack,
                    actions = buildList {
                        if (collection == null) {
                            add(
                                TopBarAction(label = "Export", icon = Icons.Filled.FileUpload) {
                                    exportLauncher.launch("$title.m3u")
                                },
                            )
                        }
                        collection?.let { target ->
                            val disabledActions = target.disabledActions()
                            target.actions().forEach { action ->
                                add(
                                    TopBarAction(label = action.label, icon = action.icon, enabled = action !in disabledActions) {
                                        collectionActions.perform(target, action)
                                    },
                                )
                            }
                        }
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
                            playlist?.let {
                                TopBarAction(label = "Remove from playlist", icon = Icons.Filled.RemoveCircleOutline) {
                                    val tracksById = tracks.itemsOrEmpty.associateBy { it.id }
                                    removingTracks = selection.selectedKeys.filterIsInstance<SelectionKey.Track>()
                                        .mapNotNull { tracksById[it.trackId] }
                                }
                            },
                        ),
                        onMoreOptions = openSelectionOptions,
                    ),
                    collapse = collapse,
                )
            },
            header = {
                DetailHeader(
                    cover = header.cover,
                    type = header.type,
                    name = title,
                    subhead = header.subhead,
                    info = header.info,
                    onPlay = { viewModel.onPlayAll(shuffled = false) },
                    onShuffle = { viewModel.onPlayAll(shuffled = true) },
                    playLabel = "Play",
                    shuffleLabel = "Shuffle",
                    isPlayable = hasTracks,
                )
            },
            isHeaderAside = searchQuery != null,
            contentKey = sections to visibleTracks,
            dragSelection = rememberDragSelection(
                selection = selection,
                listState = headerState.listState,
                orderedKeys = remember(visibleTracks) { visibleTracks.itemsOrEmpty.map { SelectionKey.Track(it.id) } },
            ),
        ) {
            if (searchQuery == null) {
                sections.forEachIndexed { index, section ->
                    if (index > 0) item(key = "divider-${section.title}") { HorizontalDivider() }
                    item(key = "heading-${section.title}") { DetailSectionHeader(title = section.title) }
                    when (section) {
                        is DetailSection.Albums -> items(section.albums, key = { "album-${it.id}" }) { album ->
                            CompositionLocalProvider(
                                LocalSwipeActions provides rememberQueueSwipeActions(collectionActions, OptionsTarget.ForAlbum(album)),
                            ) {
                                AlbumRow(
                                    album = album,
                                    selection = null,
                                    isCurrent = { playback.marks(album) },
                                    isPlaying = { playback.isPlaying },
                                    onClick = { navigator.go(AlbumDetailScreen(album.id)) },
                                    onOpenOptions = { optionsTarget = OptionsTarget.ForAlbum(album) },
                                    // Every album here is the artist's, so it is told apart by when it came out.
                                    subtitle = album.year?.toString() ?: "No date",
                                )
                            }
                        }
                        is DetailSection.Artists -> items(section.artists, key = { "artist-${it.id}" }) { artist ->
                            CompositionLocalProvider(
                                LocalSwipeActions provides rememberQueueSwipeActions(collectionActions, OptionsTarget.ForArtist(artist)),
                            ) {
                                ArtistRow(
                                    artist = artist,
                                    selection = null,
                                    isCurrent = { playback.marks(artist) },
                                    isPlaying = { playback.isPlaying },
                                    onClick = { navigator.go(ArtistDetailScreen(artist.id)) },
                                    onOpenOptions = { optionsTarget = OptionsTarget.ForArtist(artist) },
                                )
                            }
                        }
                    }
                }
                if (sections.isNotEmpty()) item(key = "divider-tracks") { HorizontalDivider() }
            }
            item(key = "heading-tracks") {
                // What acts on the tracks sits on their heading: searching them and sorting them, as one
                // group of buttons like any other. Search is inert while its field is open in the bar, so
                // the row keeps its shape; only a list whose order is its content has no sort to offer.
                val sortTracks = sort?.let { sortAction { isSortSheetOpen = true } }
                DetailSectionHeader(
                    title = "Tracks",
                    trailing = {
                        SonaIconButtonGroup {
                            iconButton(
                                icon = Icons.Filled.Search,
                                label = "Search",
                                onClick = { searchQuery = "" },
                                enabled = searchQuery == null,
                            )
                            if (sortTracks != null) {
                                iconButton(icon = sortTracks.icon, label = sortTracks.label, onClick = sortTracks.onClick)
                            }
                        }
                    },
                )
            }
            val rows = visibleTracks.itemsOrEmpty
            when {
                visibleTracks is LibraryContent.Loading -> Unit
                rows.isEmpty() -> item(key = "empty-tracks") {
                    EmptyLibraryState(title = "No tracks found", message = emptyMessage)
                }
                reorderableRows != null -> reorderableRows(reorderableRows, { track -> track.id }, trackRow)
                groupsByDisc && rows.distinctBy { it.discNumber }.size > 1 -> {
                    // Auxio's discs: the sorted tracks grouped by disc, each disc where its first track fell.
                    rows.groupBy { it.discNumber }.entries.forEachIndexed { index, (disc, discTracks) ->
                        if (index > 0) item(key = "divider-disc-$disc") { HorizontalDivider() }
                        item(key = "heading-disc-$disc") {
                            DetailSectionHeader(title = disc?.let { "Disc $it" } ?: "No disc")
                        }
                        items(discTracks, key = { it.id }, contentType = { LIST_ROW_CONTENT_TYPE }) { trackRow(it) }
                    }
                }
                else -> items(rows, key = { it.id }, contentType = { LIST_ROW_CONTENT_TYPE }) { trackRow(it) }
            }
        }
    }

    if (isSortSheetOpen && sort != null) {
        SortSheet(sort = sort, onDismiss = { isSortSheetOpen = false })
    }

    optionsTarget?.let { target ->
        OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
    }

    // Nothing is left here to show once the collection this screen is showing has been deleted.
    OptionsFollowUps(actions = collectionActions, onCollectionDeleted = onBack)

    if (playlist != null && removingTracks.isNotEmpty()) {
        RemoveFromPlaylistDialog(
            playlist = playlist,
            trackIds = removingTracks.map { it.id },
            onDismiss = { removingTracks = emptyList() },
            // The selection ends once the removal is confirmed; cancelling leaves every row picked.
            onConfirmed = { selection.clear() },
        )
    }
}

/** Narrows a loaded list, leaving "still loading" alone so a search cannot look like an empty library. */
internal fun <T> LibraryContent<T>.filterItems(predicate: (T) -> Boolean): LibraryContent<T> =
    when (this) {
        is LibraryContent.Loading -> this
        is LibraryContent.Ready -> LibraryContent.Ready(items.filter(predicate))
    }

/** Whether a row showing [texts] belongs on screen while [query] is being searched for. */
internal fun matchesSearch(query: String?, vararg texts: String): Boolean =
    query.isNullOrBlank() || texts.any { it.contains(query, ignoreCase = true) }

/** What an empty list says - the library being empty, or the search matching nothing. */
internal fun searchEmptyMessage(query: String?): String =
    if (query.isNullOrBlank()) "Add some music to your device to see it here." else "Nothing matched \"$query\"."
