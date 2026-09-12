package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SelectionState

@Composable
fun TracksScreen(
    viewModel: LibraryViewModel,
    selection: SelectionState,
    modifier: Modifier = Modifier,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    LibraryList(
        content = tracks,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No tracks found",
        emptyMessage = "Add some music to your device to see it here.",
        key = { it.id },
        modifier = modifier,
        rowsShowCoverArt = true,
    ) { track ->
        TrackRow(
            track = track,
            // Lambdas, so changing songs recomposes two rows instead of the whole list.
            isCurrent = { track.id == currentTrackId },
            isPlaying = { isPlaying },
            selection = selection,
            onClick = { viewModel.onTrackClick(track) },
        )
    }
}
