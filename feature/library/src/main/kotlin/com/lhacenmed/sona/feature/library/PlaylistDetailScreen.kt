package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCover
import com.lhacenmed.sona.core.designsystem.component.coverSeed
import com.lhacenmed.sona.core.designsystem.component.title
import com.lhacenmed.sona.core.model.BuiltInList
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.R
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.options.PlaylistOptionsContext
import com.lhacenmed.sona.feature.library.options.RemovableTrackList

/**
 * One playlist's tracks, in the order they are sorted - the ones added last first, until the user picks
 * another order or arranges them by hand. Favorites arrives here too.
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
            title = playlist?.title ?: stringResource(R.string.options_type_playlist),
            header = DetailHeaderContent(
                type = stringResource(R.string.options_type_playlist),
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
            emptyMessage = stringResource(R.string.playlist_empty_message),
            collection = playlist?.let { OptionsTarget.ForPlaylist(it, PlaylistOptionsContext.FROM_DETAIL) },
        )
    }
}

/** Everything ever played, most recent first. */
data object RecentlyPlayedScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: RecentlyPlayedViewModel = hiltViewModel()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = BuiltInList.RECENTLY_PLAYED.title,
            header = listeningHistoryHeader(tracks.itemsOrEmpty, BuiltInList.RECENTLY_PLAYED),
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = stringResource(R.string.recently_played_empty),
            collection = null,
            removableList = RemovableTrackList.RecentlyPlayed,
        )
    }
}

/** The tracks listened to right through, most often first. */
data object MostPlayedScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: MostPlayedViewModel = hiltViewModel()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = BuiltInList.MOST_PLAYED.title,
            header = listeningHistoryHeader(tracks.itemsOrEmpty, BuiltInList.MOST_PLAYED),
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = stringResource(R.string.most_played_empty),
            collection = null,
            removableList = RemovableTrackList.MostPlayed,
        )
    }
}

/**
 * Recent's and Most played's header: no collection of the library's own, so named for what they are,
 * with a cover stacked from the tracks they hold - as the playlists tab draws their rows.
 */
@Composable
private fun listeningHistoryHeader(tracks: List<Track>, list: BuiltInList) = DetailHeaderContent(
    type = stringResource(R.string.listening_history),
    subhead = null,
    info = trackCountAndDuration(tracks),
    cover = {
        SonaPlaylistCover(
            coverArtUris = rankedCoverArtUris(tracks.map { it.coverArtUri }),
            seed = list.coverSeed,
            size = CoverArtDefaults.DetailHeaderSize,
            cornerRadius = CoverArtDefaults.DetailHeaderCornerRadius,
        )
    },
)
