package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

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
            subtitle = "${tracks.itemsOrEmpty.size} tracks",
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This album has no tracks.",
        )
    }
}
