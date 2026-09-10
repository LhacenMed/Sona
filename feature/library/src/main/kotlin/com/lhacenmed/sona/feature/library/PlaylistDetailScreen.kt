package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
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

        TrackListDetail(
            title = playlist?.name ?: "Playlist",
            subtitle = "${tracks.itemsOrEmpty.size} tracks",
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This playlist has no tracks yet.",
        )
    }
}

/** The Favourites playlist, reached from the shortcut rather than by id. */
object FavoritesScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: FavoritesViewModel = hiltViewModel()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = "Favorites",
            subtitle = "${tracks.itemsOrEmpty.size} tracks",
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "Tap the heart on a track to keep it here.",
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
