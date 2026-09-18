package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.options.PlaylistOptionsContext

/**
 * One playlist's tracks, in the order they were arranged. Favorites arrives here too.
 *
 * Its menu is the playlist's own options, the ones its row's sheet offers - adding tracks, playing,
 * editing, importing, exporting and deleting - so the two can never disagree.
 */
data class PlaylistDetailScreen(val playlistId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(playlistId) },
        )
        val playlist by viewModel.playlist.collectAsStateWithLifecycle()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = playlist?.name ?: "Playlist",
            subtitle = "${tracks.itemsOrEmpty.size} tracks",
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This playlist has no tracks yet.",
            collection = playlist?.let { OptionsTarget.ForPlaylist(it, PlaylistOptionsContext.FROM_DETAIL) },
            // Only a playlist has an order of its own to rearrange, and membership to remove from.
            // Dragging works whatever the list is sorted by: the drop stores the order it ended on and
            // puts the list in it, so what was dragged is what stays.
            onReorder = { reordered -> viewModel.setOrder(reordered.map { it.id }) },
            removeFromPlaylist = viewModel::removeFromPlaylist,
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
            collection = null,
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
            collection = null,
        )
    }
}
