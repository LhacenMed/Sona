package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCover
import com.lhacenmed.sona.core.model.Track
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
            header = DetailHeaderContent(
                type = "Playlist",
                subhead = null,
                info = trackCountAndDuration(tracks.itemsOrEmpty),
                cover = {
                    SonaPlaylistCover(
                        coverArtUris = playlist?.coverArtUris.orEmpty(),
                        seed = playlistId.hashCode(),
                        size = CoverArtDefaults.DetailHeaderSize,
                        cornerRadius = CoverArtDefaults.DetailHeaderCornerRadius,
                    )
                },
            ),
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This playlist has no tracks yet.",
            collection = playlist?.let { OptionsTarget.ForPlaylist(it, PlaylistOptionsContext.FROM_DETAIL) },
            // Only a playlist has an order of its own to rearrange.
            // Dragging works whatever the list is sorted by: the drop stores the order it ended on and
            // puts the list in it, so what was dragged is what stays.
            onReorder = { reordered -> viewModel.setOrder(reordered.map { it.id }) },
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
            header = listeningHistoryHeader(tracks.itemsOrEmpty, seed = "recent"),
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
            header = listeningHistoryHeader(tracks.itemsOrEmpty, seed = "mostPlayed"),
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "Nothing has been played right through yet.",
            collection = null,
        )
    }
}

/**
 * Recent's and Most played's header: no collection of the library's own, so named for what they are,
 * with a cover stacked from the tracks they hold - as the playlists tab draws their rows.
 */
private fun listeningHistoryHeader(tracks: List<Track>, seed: String) = DetailHeaderContent(
    type = "Listening history",
    subhead = null,
    info = trackCountAndDuration(tracks),
    cover = {
        SonaPlaylistCover(
            coverArtUris = rankedCoverArtUris(tracks.map { it.coverArtUri }),
            seed = seed.hashCode(),
            size = CoverArtDefaults.DetailHeaderSize,
            cornerRadius = CoverArtDefaults.DetailHeaderCornerRadius,
        )
    },
)
