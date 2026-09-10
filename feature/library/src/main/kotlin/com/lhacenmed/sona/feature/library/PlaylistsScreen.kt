package com.lhacenmed.sona.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

/** The derived lists' titles, which are also what a search matches them on. */
private const val RECENT_TITLE = "Recent"
private const val MOST_PLAYED_TITLE = "Most played"

/** What the name dialog is currently being used for, since create, rename and import all need one. */
private sealed interface NamePrompt {
    data object Create : NamePrompt
    data class FromFolder(val folderPath: String) : NamePrompt
    data class Import(val source: Uri) : NamePrompt
    data class Rename(val playlist: Playlist) : NamePrompt
}

/**
 * Every collection of tracks the user can open: the two derived lists, then the playlists
 * themselves with Favourites at the top.
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
        val selection = rememberSelectionState()
        val context = LocalContext.current

        var searchQuery by remember { mutableStateOf<String?>(null) }
        var namePrompt by remember { mutableStateOf<NamePrompt?>(null) }
        var confirmingDelete by remember { mutableStateOf<List<Playlist>>(emptyList()) }

        val folderLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            val path = uri?.let { documentPathOrNull(it, isTree = true) }
            if (path != null) namePrompt = NamePrompt.FromFolder(path)
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) namePrompt = NamePrompt.Import(uri)
        }

        // The derived lists are rows here rather than playlists, so they are matched on their own
        // titles - otherwise typing "recent" would hide the very row it names.
        val query = searchQuery.orEmpty()
        fun matchesQuery(text: String) = query.isBlank() || text.contains(query, ignoreCase = true)

        val visiblePlaylists = playlists.filterItems { matchesQuery(it.name) }
        val selectedPlaylists = playlists.itemsOrEmpty.filter { it.id in selection.selectedKeys }
        val deletablePlaylists = selectedPlaylists.filterNot { it.isBuiltIn }
        val renameTarget = selectedPlaylists.singleOrNull()?.takeUnless { it.isBuiltIn }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = "Playlists",
                onNavigateBack = navigator::back,
                actions = listOf(
                    TopBarAction(label = "Search", icon = Icons.Filled.Search) { searchQuery = "" },
                    SortPlaceholderAction,
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
                        importLauncher.launch(arrayOf(M3U_MIME_TYPE, "*/*"))
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
                // Favourites - the same rule the queries enforce, surfaced so it never looks broken.
                selection = selection.toTopBarSelection(
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
                modifier = Modifier.fillMaxSize(),
            ) { items ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (matchesQuery(RECENT_TITLE)) {
                        item(key = "recently-played") {
                            LibraryEntityRow(
                                // Not a playlist, so it takes no part in a playlist selection.
                                selection = null,
                                selectionKey = "recently-played",
                                title = RECENT_TITLE,
                                subtitle = "$recentlyPlayedCount tracks",
                                onClick = { navigator.go(RecentlyPlayedScreen) },
                            )
                        }
                    }
                    if (matchesQuery(MOST_PLAYED_TITLE)) {
                        item(key = "most-played") {
                            LibraryEntityRow(
                                selection = null,
                                selectionKey = "most-played",
                                title = MOST_PLAYED_TITLE,
                                subtitle = "$mostPlayedCount tracks",
                                onClick = { navigator.go(MostPlayedScreen) },
                            )
                        }
                    }
                    items(items = items, key = { "playlist-${it.id}" }) { playlist ->
                        LibraryEntityRow(
                            selection = selection,
                            selectionKey = playlist.id,
                            title = playlist.name,
                            subtitle = "${playlist.trackCount} tracks",
                            onClick = { navigator.go(PlaylistDetailScreen(playlist.id)) },
                        )
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
                    is NamePrompt.Import -> "Imported playlist"
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
                        is NamePrompt.Import -> viewModel.importPlaylist(name) {
                            context.contentResolver.openInputStream(prompt.source)
                        }
                        is NamePrompt.Rename -> viewModel.renamePlaylist(prompt.playlist.id, name)
                    }
                    namePrompt = null
                    selection.clear()
                },
            )
        }

        if (confirmingDelete.isNotEmpty()) {
            val doomed = confirmingDelete
            AlertDialog(
                onDismissRequest = { confirmingDelete = emptyList() },
                title = { Text("Remove playlist") },
                text = {
                    Text(
                        if (doomed.size == 1) {
                            "This removes \"${doomed.single().name}\". The tracks themselves are not deleted."
                        } else {
                            "This removes ${doomed.size} playlists. The tracks themselves are not deleted."
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deletePlaylists(doomed.map { it.id })
                            confirmingDelete = emptyList()
                            selection.clear()
                        },
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingDelete = emptyList() }) { Text("Cancel") }
                },
            )
        }
    }
}
