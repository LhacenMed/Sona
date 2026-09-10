package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

data class ArtistDetailScreen(val artistId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(artistId) },
        )
        val artist by viewModel.artist.collectAsStateWithLifecycle()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = artist?.name ?: "Artist",
            subtitle = "${tracks.itemsOrEmpty.size} tracks",
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This artist has no tracks.",
        )
    }
}
