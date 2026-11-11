package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SonaAlbumCover
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.R
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
            title = album?.title ?: stringResource(R.string.options_type_album),
            header = DetailHeaderContent(
                type = stringResource(R.string.options_type_album),
                subhead = album?.artistName,
                info = listOf(
                    album?.year?.toString() ?: stringResource(R.string.library_no_date),
                    trackCountAndDuration(tracks.itemsOrEmpty),
                ).joinToString(InfoSeparator),
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
            emptyMessage = stringResource(R.string.album_empty),
            collection = album?.let { OptionsTarget.ForAlbum(it, AlbumOptionsContext.FROM_DETAIL) },
            trackOptionsContext = TrackOptionsContext.FROM_ALBUM,
            groupsByDisc = true,
        )
    }
}
