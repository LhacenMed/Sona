package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaGenreCover
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.options.GenreOptionsContext
import com.lhacenmed.sona.feature.library.options.OptionsTarget

data class GenreDetailScreen(val genreId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<GenreDetailViewModel, GenreDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(genreId) },
        )
        val genre by viewModel.genre.collectAsStateWithLifecycle()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = genre?.name ?: "Genre",
            header = DetailHeaderContent(
                type = "Genre",
                subhead = null,
                info = listOf(
                    pluralCount(genre?.artistCount ?: 0, "artist"),
                    trackCountLabel(tracks.itemsOrEmpty.size),
                ).joinToString(DETAIL_INFO_SEPARATOR),
                cover = {
                    SonaGenreCover(
                        coverArtUris = genre?.coverArtUris.orEmpty(),
                        seed = genreId.hashCode(),
                        size = CoverArtDefaults.DetailHeaderSize,
                        cornerRadius = CoverArtDefaults.DetailHeaderCornerRadius,
                    )
                },
            ),
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This genre has no tracks.",
            collection = genre?.let { OptionsTarget.ForGenre(it, GenreOptionsContext.FROM_DETAIL) },
        )
    }
}
