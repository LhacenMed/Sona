package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

data class AlbumDetailScreen(val albumId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(albumId) },
        )
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

        Column(modifier = Modifier.fillMaxSize()) {
            DetailHeader(
                title = uiState.album?.title ?: "Album",
                subtitle = "${uiState.tracks.size} tracks",
                onBack = navigator::back,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.tracks.isEmpty()) {
                    EmptyLibraryState(
                        title = "No tracks found",
                        message = "This album has no tracks.",
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.tracks.size) { index ->
                            val track = uiState.tracks[index]
                            DetailTrackRow(
                                track = track,
                                isPlaying = track.id == playbackState.currentTrackId,
                                onClick = { viewModel.onTrackClick(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}
