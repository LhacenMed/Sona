package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaAlbumCover
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.options.AlbumOptionsContext
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.options.TrackOptionsContext

data class AlbumDetailScreen(val albumId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(albumId) },
        )
        val album by viewModel.album.collectAsStateWithLifecycle()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = album?.title ?: "Album",
            header = DetailHeaderContent(
                type = "Album",
                subhead = album?.artistName,
                info = listOf(
                    album?.year?.toString() ?: "No date",
                    trackCountAndDuration(tracks.itemsOrEmpty),
                ).joinToString(DETAIL_INFO_SEPARATOR),
                cover = {
                    SonaAlbumCover(
                        coverArtUri = album?.coverArtUri,
                        size = CoverArtDefaults.DetailHeaderSize,
                        cornerRadius = CoverArtDefaults.DetailHeaderCornerRadius,
                    )
                },
            ),
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This album has no tracks.",
            collection = album?.let { OptionsTarget.ForAlbum(it, AlbumOptionsContext.FROM_DETAIL) },
            trackOptionsContext = TrackOptionsContext.FROM_ALBUM,
            groupsByDisc = true,
        )
    }
}
