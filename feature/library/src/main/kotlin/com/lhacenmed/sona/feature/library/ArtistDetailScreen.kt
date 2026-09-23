package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaArtistCover
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.options.ArtistOptionsContext
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.options.TrackOptionsContext

data class ArtistDetailScreen(val artistId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(artistId) },
        )
        val artist by viewModel.artist.collectAsStateWithLifecycle()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
        val genreNames by viewModel.genreNames.collectAsStateWithLifecycle()

        TrackListDetail(
            title = artist?.name ?: "Artist",
            header = DetailHeaderContent(
                type = "Artist",
                subhead = genreNames.joinToString(", ").ifEmpty { null },
                info = listOf(
                    pluralCount(artist?.albumCount ?: 0, "album"),
                    trackCountLabel(tracks.itemsOrEmpty.size),
                ).joinToString(DETAIL_INFO_SEPARATOR),
                cover = {
                    SonaArtistCover(
                        coverArtUris = artist?.coverArtUris.orEmpty(),
                        seed = artistId.hashCode(),
                        size = CoverArtDefaults.DetailHeaderSize,
                    )
                },
            ),
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This artist has no tracks.",
            collection = artist?.let { OptionsTarget.ForArtist(it, ArtistOptionsContext.FROM_DETAIL) },
            trackOptionsContext = TrackOptionsContext.FROM_ARTIST,
            // Every track here is the artist's, so each is told apart by its album - as Auxio's are.
            trackSubtitle = { it.album },
        )
    }
}
