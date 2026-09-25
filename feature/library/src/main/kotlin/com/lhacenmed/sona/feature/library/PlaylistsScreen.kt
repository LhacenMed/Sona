package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.FastScroller
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.LocalDragSelection
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.dragSelection
import com.lhacenmed.sona.core.designsystem.component.rememberDragSelection
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.operation.DeletePlaylistsDialog
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.playlist.NewPlaylistFlow
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.selection.SelectionOptionsHost
import com.lhacenmed.sona.feature.library.selection.selectionKeyOf
import com.lhacenmed.sona.feature.library.selection.toLibraryTopBarSelection
import com.lhacenmed.sona.feature.library.sort.SortSheet
import com.lhacenmed.sona.feature.library.sort.sortAction

/** Most played's title, which is also what a search matches it on. */
private const val MOST_PLAYED_TITLE = "Most played"

/** A row of the list: Most played pinned first, then the playlists. */
private sealed interface PlaylistsRow {
    data object MostPlayed : PlaylistsRow

    data class OfPlaylist(val playlist: Playlist) : PlaylistsRow
}

/**
 * The playlists the user made, with Most played pinned above them.
 *
 * Favorites and Recent are not listed: each is a shortcut card of its own on the library screen, one
 * tap away already. Most played is only pinned once something has actually been counted in it, so an
 * install with nothing played yet and no playlists shows the empty state, which offers to create one.
 *
 * Creating a playlist is one button, whichever way it starts - empty, from a folder or from a playlist
 * file - see [NewPlaylistFlow]. Importing a file into a playlist that already exists is that playlist's
 * own option.
 *
 * Reached from the Playlists shortcut rather than a tab - the tabs browse the library by one of its
 * own dimensions, and a playlist is not one of those, it is something the user made.
 */
data object PlaylistsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: PlaylistsViewModel = hiltViewModel()
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        val mostPlayedCount by viewModel.mostPlayedCount.collectAsStateWithLifecycle()
        val mostPlayedCoverArtUris by viewModel.mostPlayedCoverArtUris.collectAsStateWithLifecycle()
        val playback by viewModel.playback.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()
        val listState = rememberLazyListState()

        var searchQuery by remember { mutableStateOf<String?>(null) }
        var isCreatingPlaylist by remember { mutableStateOf(false) }
        var renamingPlaylist by remember { mutableStateOf<Playlist?>(null) }
        var confirmingDelete by remember { mutableStateOf<List<Playlist>>(emptyList()) }
        var isSortSheetOpen by remember { mutableStateOf(false) }
        var optionsTarget by remember { mutableStateOf<OptionsTarget.ForPlaylist?>(null) }

        // Most played is a row here rather than a playlist, so it is matched on its own title -
        // otherwise typing "most" would hide the very row it names.
        val query = searchQuery.orEmpty()
        fun matchesQuery(text: String) = query.isBlank() || text.contains(query, ignoreCase = true)

        val visiblePlaylists = playlists.filterItems { !it.isBuiltIn && matchesQuery(it.name) }
        val showsMostPlayed = mostPlayedCount > 0 && matchesQuery(MOST_PLAYED_TITLE)
        val rows: LibraryContent<PlaylistsRow> = when (visiblePlaylists) {
            is LibraryContent.Loading -> LibraryContent.Loading
            is LibraryContent.Ready -> LibraryContent.Ready(
                listOfNotNull(PlaylistsRow.MostPlayed.takeIf { showsMostPlayed }) +
                    visiblePlaylists.items.map(PlaylistsRow::OfPlaylist),
            )
        }
        val selectedPlaylists = visiblePlaylists.itemsOrEmpty.filter { SelectionKey.Playlist(it.id) in selection.selectedKeys }

        SelectionOptionsHost(selection) { openSelectionOptions ->
            Column(modifier = Modifier.fillMaxSize()) {
                SonaTopAppBar(
                    title = "Playlists",
                    onNavigateBack = navigator::back,
                    actions = listOf(
                        TopBarAction(label = "Search", icon = Icons.Filled.Search) { searchQuery = "" },
                        sortAction { isSortSheetOpen = true },
                        TopBarAction(label = "New playlist", icon = Icons.Filled.Add) { isCreatingPlaylist = true },
                    ),
                    search = searchQuery?.let { current ->
                        TopBarSearch(
                            query = current,
                            onQueryChange = { searchQuery = it },
                            onClose = { searchQuery = null },
                        )
                    },
                    // Rename needs exactly one playlist to rename.
                    selection = selection.toLibraryTopBarSelection(
                        listKeys = {
                            visiblePlaylists.itemsOrEmpty.filter { it.trackCount > 0 }.map { SelectionKey.Playlist(it.id) }
                        },
                        onMoreOptions = openSelectionOptions,
                        actions = buildList {
                            selectedPlaylists.singleOrNull()?.let { playlist ->
                                add(
                                    TopBarAction(label = "Rename", icon = Icons.Filled.DriveFileRenameOutline) {
                                        renamingPlaylist = playlist
                                    },
                                )
                            }
                            if (selectedPlaylists.isNotEmpty()) {
                                add(
                                    TopBarAction(label = "Delete", icon = Icons.Filled.Delete) {
                                        confirmingDelete = selectedPlaylists
                                    },
                                )
                            }
                        },
                    ),
                )

                LibraryListContent(
                    content = rows,
                    // A playlist exists whether or not the library has been scanned, so neither the
                    // permission nor the scanning explanation can apply to this list being empty.
                    hasPermission = true,
                    isScanning = false,
                    emptyTitle = if (searchQuery == null) "No playlists yet" else "No playlists found",
                    emptyMessage = if (searchQuery == null) {
                        "Create one to start collecting tracks."
                    } else {
                        searchEmptyMessage(searchQuery)
                    },
                    loadingIcon = SonaIcons.Playlist,
                    modifier = Modifier.fillMaxSize(),
                    // Only while not searching: a search that matches nothing is not a missing playlist.
                    emptyAction = if (searchQuery == null) {
                        EmptyStateAction(label = "Create playlist", icon = Icons.Filled.Add) { isCreatingPlaylist = true }
                    } else {
                        null
                    },
                ) { items ->
                    val selectableKeys = remember(items) {
                        items.mapNotNull { (it as? PlaylistsRow.OfPlaylist)?.playlist?.let(::selectionKeyOf) }
                    }
                    val dragSelection = rememberDragSelection(selection, listState, selectableKeys)
                    FastScroller(listState = listState, modifier = Modifier.fillMaxSize()) {
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
                                    key = { row ->
                                        when (row) {
                                            PlaylistsRow.MostPlayed -> "most-played"
                                            is PlaylistsRow.OfPlaylist -> "playlist-${row.playlist.id}"
                                        }
                                    },
                                ) { row ->
                                    when (row) {
                                        PlaylistsRow.MostPlayed -> TrackCollectionRow(
                                            title = MOST_PLAYED_TITLE,
                                            trackCount = mostPlayedCount,
                                            coverArtUris = mostPlayedCoverArtUris,
                                            isCurrent = { playback.marks(PlaybackParent.MostPlayed) },
                                            isPlaying = { playback.isPlaying },
                                            onClick = { navigator.go(MostPlayedScreen) },
                                        )

                                        is PlaylistsRow.OfPlaylist -> PlaylistRow(
                                            playlist = row.playlist,
                                            selection = selection,
                                            isCurrent = { playback.marks(row.playlist) },
                                            isPlaying = { playback.isPlaying },
                                            onClick = { navigator.go(PlaylistDetailScreen(row.playlist.id)) },
                                            onOpenOptions = { optionsTarget = OptionsTarget.ForPlaylist(row.playlist) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isCreatingPlaylist) {
            NewPlaylistFlow(onFinished = { isCreatingPlaylist = false })
        }

        renamingPlaylist?.let { playlist ->
            PlaylistNameDialog(
                dialogTitle = "Rename playlist",
                confirmLabel = "Rename",
                initialName = playlist.name,
                // Its own name is not "taken" by anything else, so renaming without changing it
                // is allowed rather than reported as a clash.
                takenNames = playlists.itemsOrEmpty.map { it.name } - playlist.name,
                onDismiss = { renamingPlaylist = null },
                onConfirm = { name ->
                    viewModel.renamePlaylist(playlist.id, name)
                    renamingPlaylist = null
                    selection.clear()
                },
            )
        }

        if (isSortSheetOpen) {
            SortSheet(sort = viewModel.sort, onDismiss = { isSortSheetOpen = false })
        }

        optionsTarget?.let { target ->
            OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
        }

        if (confirmingDelete.isNotEmpty()) {
            DeletePlaylistsDialog(
                playlists = confirmingDelete,
                onDismiss = { confirmingDelete = emptyList() },
                onConfirmed = { selection.clear() },
            )
        }
    }
}
