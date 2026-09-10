package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TracksScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle()

    LibraryList(
        content = tracks,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No tracks found",
        emptyMessage = "Add some music to your device to see it here.",
        key = { it.id },
        modifier = modifier,
    ) { track ->
        TrackRow(
            track = track,
            // A lambda, so changing songs recomposes two rows instead of the whole list.
            isPlaying = { track.id == currentTrackId },
            onClick = { viewModel.onTrackClick(track) },
        )
    }
}
