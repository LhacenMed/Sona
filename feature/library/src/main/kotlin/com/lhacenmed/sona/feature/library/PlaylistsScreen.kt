package com.lhacenmed.sona.feature.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.storage.documentPathOrNull
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.FastScroller
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.operation.DeletePlaylistsDialog
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.selection.SelectionOptionsHost
import com.lhacenmed.sona.feature.library.selection.toLibraryTopBarSelection
import com.lhacenmed.sona.feature.library.sort.SortSheet
import com.lhacenmed.sona.feature.library.sort.sortAction

/** The derived lists' titles, which are also what a search matches them on. */
private const val RECENT_TITLE = "Recent"
private const val MOST_PLAYED_TITLE = "Most played"

/** What the name dialog is currently being used for, since create and rename both need one. */
private sealed interface NamePrompt {
    data object Create : NamePrompt
    data class FromFolder(val folderPath: String) : NamePrompt
    data class Rename(val playlist: Playlist) : NamePrompt
}

/**
 * Every collection of tracks the user can open: the two derived lists, then the playlists
 * themselves with Favorites at the top.
 *
 * Reached from the Playlists shortcut rather than a tab - the tabs browse the library by one of its
 * own dimensions, and a playlist is not one of those, it is something the user made.
 */
object PlaylistsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: PlaylistsViewModel = hiltViewModel()
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        val recentlyPlayedCount by viewModel.recentlyPlayedCount.collectAsStateWithLifecycle()
        val mostPlayedCount by viewModel.mostPlayedCount.collectAsStateWithLifecycle()
        val recentlyPlayedCoverArtUris by viewModel.recentlyPlayedCoverArtUris.collectAsStateWithLifecycle()
        val mostPlayedCoverArtUris by viewModel.mostPlayedCoverArtUris.collectAsStateWithLifecycle()
        val playback by viewModel.playback.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()
        val listState = rememberLazyListState()
        val context = LocalContext.current

        var searchQuery by remember { mutableStateOf<String?>(null) }
        var namePrompt by remember { mutableStateOf<NamePrompt?>(null) }
        var confirmingDelete by remember { mutableStateOf<List<Playlist>>(emptyList()) }
        var isSortSheetOpen by remember { mutableStateOf(false) }
        var optionsTarget by remember { mutableStateOf<OptionsTarget.ForPlaylist?>(null) }
        // The file waiting to be imported, and whether its destination is being named. Both dialogs
        // are on screen at once while naming, the destinations still behind the name.
        var importSource by remember { mutableStateOf<Uri?>(null) }
        var isNamingNewPlaylist by remember { mutableStateOf(false) }

        fun showImportResult(succeeded: Boolean) {
            val message = if (succeeded) "Playlist imported" else "Could not import playlist"
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }

        val folderLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            val path = uri?.let { documentPathOrNull(it, isTree = true) }
            if (path != null) namePrompt = NamePrompt.FromFolder(path)
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) importSource = uri
        }

        // The derived lists are rows here rather than playlists, so they are matched on their own
        // titles - otherwise typing "recent" would hide the very row it names.
        val query = searchQuery.orEmpty()
        fun matchesQuery(text: String) = query.isBlank() || text.contains(query, ignoreCase = true)

        val visiblePlaylists = playlists.filterItems { matchesQuery(it.name) }
        val selectedPlaylists = playlists.itemsOrEmpty.filter { SelectionKey.Playlist(it.id) in selection.selectedKeys }
        val deletablePlaylists = selectedPlaylists.filterNot { it.isBuiltIn }
        val renameTarget = selectedPlaylists.singleOrNull()?.takeUnless { it.isBuiltIn }

        SelectionOptionsHost(selection) { openSelectionOptions ->
            Column(modifier = Modifier.fillMaxSize()) {
                SonaTopAppBar(
                    title = "Playlists",
                    onNavigateBack = navigator::back,
                    actions = listOf(
                        TopBarAction(label = "Search", icon = Icons.Filled.Search) { searchQuery = "" },
                        sortAction { isSortSheetOpen = true },
                        TopBarAction(label = "Create new playlist", icon = Icons.Filled.Add) {
                            namePrompt = NamePrompt.Create
                        },
                        TopBarAction(
                            label = "Create new playlist from folder",
                            icon = Icons.Filled.CreateNewFolder,
                        ) {
                            folderLauncher.launch(null)
                        },
                        TopBarAction(label = "Import playlist", icon = Icons.Filled.FileDownload) {
                            importLauncher.launch(M3U_PICKER_MIME_TYPES)
                        },
                    ),
                    search = searchQuery?.let { current ->
                        TopBarSearch(
                            query = current,
                            onQueryChange = { searchQuery = it },
                            onClose = { searchQuery = null },
                        )
                    },
                    // Rename needs exactly one playlist to rename, and neither action is offered for
                    // Favorites - the same rule the queries enforce, surfaced so it never looks broken.
                    selection = selection.toLibraryTopBarSelection(
                        listKeys = {
                            visiblePlaylists.itemsOrEmpty.filter { it.trackCount > 0 }.map { SelectionKey.Playlist(it.id) }
                        },
                        onMoreOptions = openSelectionOptions,
                        actions = buildList {
                            if (renameTarget != null) {
                                add(
                                    TopBarAction(
                                        label = "Rename",
                                        icon = Icons.Filled.DriveFileRenameOutline,
                                    ) {
                                        namePrompt = NamePrompt.Rename(renameTarget)
                                    },
                                )
                            }
                            if (deletablePlaylists.isNotEmpty()) {
                                add(
                                    TopBarAction(label = "Delete", icon = Icons.Filled.Delete) {
                                        confirmingDelete = deletablePlaylists
                                    },
                                )
                            }
                        },
                    ),
                )

                LibraryListContent(
                    content = visiblePlaylists,
                    // A playlist exists whether or not the library has been scanned, so neither the
                    // permission nor the scanning explanation can apply to this list being empty.
                    hasPermission = true,
                    isScanning = false,
                    emptyTitle = "No playlists yet",
                    emptyMessage = "Create one to start collecting tracks.",
                    loadingIcon = SonaIcons.Playlist,
                    modifier = Modifier.fillMaxSize(),
                ) { items ->
                    FastScroller(listState = listState, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                        ) {
                            if (matchesQuery(RECENT_TITLE)) {
                                item(key = "recently-played") {
                                    TrackCollectionRow(
                                        title = RECENT_TITLE,
                                        trackCount = recentlyPlayedCount,
                                        coverArtUris = recentlyPlayedCoverArtUris,
                                        isCurrent = { playback.marks(PlaybackParent.RecentlyPlayed) },
                                        isPlaying = { playback.isPlaying },
                                        onClick = { navigator.go(RecentlyPlayedScreen) },
                                    )
                                }
                            }
                            if (matchesQuery(MOST_PLAYED_TITLE)) {
                                item(key = "most-played") {
                                    TrackCollectionRow(
                                        title = MOST_PLAYED_TITLE,
                                        trackCount = mostPlayedCount,
                                        coverArtUris = mostPlayedCoverArtUris,
                                        isCurrent = { playback.marks(PlaybackParent.MostPlayed) },
                                        isPlaying = { playback.isPlaying },
                                        onClick = { navigator.go(MostPlayedScreen) },
                                    )
                                }
                            }
                            items(items = items, key = { "playlist-${it.id}" }) { playlist ->
                                PlaylistRow(
                                    playlist = playlist,
                                    selection = selection,
                                    isCurrent = { playback.marks(playlist) },
                                    isPlaying = { playback.isPlaying },
                                    onClick = { navigator.go(PlaylistDetailScreen(playlist.id)) },
                                    onOpenOptions = { optionsTarget = OptionsTarget.ForPlaylist(playlist) },
                                )
                            }
                    }
                    }
                }
            }
        }

        namePrompt?.let { prompt ->
            val existingNames = playlists.itemsOrEmpty.map { it.name }
            val isRename = prompt is NamePrompt.Rename
            PlaylistNameDialog(
                dialogTitle = if (isRename) "Rename playlist" else "Playlist name",
                confirmLabel = if (isRename) "Rename" else "Create",
                initialName = when (prompt) {
                    is NamePrompt.Create -> ""
                    is NamePrompt.FromFolder -> prompt.folderPath.substringAfterLast('/')
                    is NamePrompt.Rename -> prompt.playlist.name
                },
                // Its own name is not "taken" by anything else, so renaming without changing it
                // is allowed rather than reported as a clash.
                takenNames = if (prompt is NamePrompt.Rename) {
                    existingNames - prompt.playlist.name
                } else {
                    existingNames
                },
                onDismiss = { namePrompt = null },
                onConfirm = { name ->
                    when (prompt) {
                        is NamePrompt.Create -> viewModel.createPlaylist(name)
                        is NamePrompt.FromFolder ->
                            viewModel.createPlaylistFromFolder(name, prompt.folderPath)
                        is NamePrompt.Rename -> viewModel.renamePlaylist(prompt.playlist.id, name)
                    }
                    namePrompt = null
                    selection.clear()
                },
            )
        }

        importSource?.let { source ->
            val openSource = { context.contentResolver.openInputStream(source) }

            ImportDestinationDialog(
                playlists = playlists.itemsOrEmpty,
                onDismiss = {
                    importSource = null
                    isNamingNewPlaylist = false
                },
                onPlaylistSelected = { playlist ->
                    importSource = null
                    viewModel.importIntoPlaylist(playlist.id, openSource, ::showImportResult)
                },
                onCreateNewSelected = { isNamingNewPlaylist = true },
            )

            if (isNamingNewPlaylist) {
                PlaylistNameDialog(
                    dialogTitle = "Create new playlist",
                    confirmLabel = "Create",
                    initialName = "",
                    takenNames = playlists.itemsOrEmpty.map { it.name },
                    onDismiss = { isNamingNewPlaylist = false },
                    onConfirm = { name ->
                        isNamingNewPlaylist = false
                        importSource = null
                        viewModel.importIntoNewPlaylist(name, openSource, ::showImportResult)
                    },
                )
            }
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
