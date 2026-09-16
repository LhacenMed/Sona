package com.lhacenmed.sona.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.operation.DeletePlaylistsDialog
import com.lhacenmed.sona.feature.library.options.OptionsActionsViewModel
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.options.toast
import com.lhacenmed.sona.feature.library.playlist.AddCollectionsScreen
import com.lhacenmed.sona.feature.library.playlist.AddTracksScreen
import com.lhacenmed.sona.feature.library.playlist.EditPlaylistScreen

/**
 * One playlist's tracks, in the order they were arranged. Favorites arrives here too.
 *
 * Its menu grows what only a real playlist can do: adding tracks one by one or whole collections at
 * once, then the playlist's own options - play next, add to queue, edit, import, export and delete, as
 * its options sheet offers them. Favorites, which can never be deleted, has no Delete.
 */
data class PlaylistDetailScreen(val playlistId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val context = LocalContext.current
        val viewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(playlistId) },
        )
        val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
        val playlist by viewModel.playlist.collectAsStateWithLifecycle()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
        var isConfirmingDelete by remember { mutableStateOf(false) }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                viewModel.importFrom({ context.contentResolver.openInputStream(uri) }) { succeeded ->
                    context.toast(if (succeeded) "Playlist imported" else "Could not import playlist")
                }
            }
        }

        TrackListDetail(
            title = playlist?.name ?: "Playlist",
            subtitle = "${tracks.itemsOrEmpty.size} tracks",
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This playlist has no tracks yet.",
            // Only a playlist has an order of its own to rearrange, and membership to remove from.
            // Dragging works whatever the list is sorted by: the drop stores the order it ended on and
            // puts the list in it, so what was dragged is what stays.
            onReorder = { reordered -> viewModel.setOrder(reordered.map { it.id }) },
            removeFromPlaylist = viewModel::removeFromPlaylist,
            // Only a real playlist has membership to add to and options of its own, so these live here
            // rather than in the shared detail screen - Recent and Most played get search and export only.
            extraActions = buildList {
                add(
                    TopBarAction(label = "Add tracks", icon = Icons.AutoMirrored.Filled.PlaylistAdd) {
                        navigator.go(AddTracksScreen(playlistId))
                    },
                )
                add(
                    TopBarAction(label = "Add from collections", icon = Icons.Filled.LibraryAdd) {
                        navigator.go(AddCollectionsScreen(playlistId))
                    },
                )
                playlist?.let { loaded ->
                    add(
                        TopBarAction(label = "Play next", icon = SonaIcons.PlayNext) {
                            actionsViewModel.playNext(OptionsTarget.ForPlaylist(loaded))
                            context.toast("Track will play next")
                        },
                    )
                    add(
                        TopBarAction(label = "Add to queue", icon = SonaIcons.QueueAdd) {
                            actionsViewModel.addToQueue(OptionsTarget.ForPlaylist(loaded))
                            context.toast("Added to queue")
                        },
                    )
                }
                add(
                    TopBarAction(label = "Edit", icon = SonaIcons.Edit) {
                        navigator.go(EditPlaylistScreen(playlistId))
                    },
                )
                add(
                    TopBarAction(label = "Import", icon = SonaIcons.Import) {
                        importLauncher.launch(M3U_PICKER_MIME_TYPES)
                    },
                )
            },
            trailingActions = listOfNotNull(
                playlist?.takeUnless { it.isBuiltIn }?.let {
                    TopBarAction(label = "Delete", icon = SonaIcons.Delete) { isConfirmingDelete = true }
                },
            ),
        )

        val deleting = playlist
        if (isConfirmingDelete && deleting != null) {
            DeletePlaylistsDialog(
                playlists = listOf(deleting),
                onDismiss = { isConfirmingDelete = false },
                // Nothing is left here to show once the playlist is gone.
                onDeleted = navigator::back,
            )
        }
    }
}

/** Everything ever played, most recent first. */
object RecentlyPlayedScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: RecentlyPlayedViewModel = hiltViewModel()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = "Recently played",
            subtitle = "${tracks.itemsOrEmpty.size} tracks",
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "Nothing has been played yet.",
        )
    }
}

/** The tracks listened to right through, most often first. */
object MostPlayedScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: MostPlayedViewModel = hiltViewModel()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = "Most played",
            subtitle = "${tracks.itemsOrEmpty.size} tracks",
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "Nothing has been played right through yet.",
        )
    }
}
