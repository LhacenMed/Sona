package com.lhacenmed.sona.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.storage.documentPathOrNull
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

/** One playlist's tracks, in the order they were arranged. Favourites arrives here too. */
data class PlaylistDetailScreen(val playlistId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(playlistId) },
        )
        val playlist by viewModel.playlist.collectAsStateWithLifecycle()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        val addFileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                documentPathOrNull(uri, isTree = false)?.let(viewModel::addFile)
            }
        }
        val addFolderLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                documentPathOrNull(uri, isTree = true)?.let(viewModel::addFolder)
            }
        }

        TrackListDetail(
            title = playlist?.name ?: "Playlist",
            subtitle = "${tracks.itemsOrEmpty.size} tracks",
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This playlist has no tracks yet.",
            // Only a playlist has an order of its own to rearrange, and membership to remove from.
            onReorder = { reordered -> viewModel.setOrder(reordered.map { it.id }) },
            onRemoveSelected = viewModel::removeFromPlaylist,
            // Only a real playlist has membership to add to, so these two live here rather than in
            // the shared detail screen - Recent and Most played get search, sort and export only.
            extraActions = listOf(
                TopBarAction(label = "Add file to playlist", icon = Icons.Filled.AudioFile) {
                    addFileLauncher.launch(arrayOf("audio/*"))
                },
                TopBarAction(label = "Add folder to playlist", icon = Icons.Filled.CreateNewFolder) {
                    addFolderLauncher.launch(null)
                },
            ),
        )
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
