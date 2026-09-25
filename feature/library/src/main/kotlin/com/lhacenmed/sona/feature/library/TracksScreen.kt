package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget

@Composable
fun TracksScreen(
    viewModel: LibraryViewModel,
    selection: SelectionState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val trackSections by viewModel.trackSections.collectAsStateWithLifecycle()
    var optionsTarget by remember { mutableStateOf<OptionsTarget.ForTrack?>(null) }

    LibraryList(
        content = tracks,
        selection = selection,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No tracks found",
        emptyMessage = searchEmptyMessage(searchQuery),
        key = { it.id },
        modifier = modifier,
        listState = listState,
        loadingIcon = SonaIcons.Song,
        sectionOf = trackSections,
    ) { track ->
        TrackRow(
            track = track,
            // Lambdas, so changing songs recomposes two rows instead of the whole list.
            isCurrent = { playback.marks(track) },
            isPlaying = { playback.isPlaying },
            selection = selection,
            onClick = { viewModel.onTrackClick(track) },
            onOpenOptions = {
                optionsTarget = OptionsTarget.ForTrack(track, queueSource = tracks.itemsOrEmpty)
            },
        )
    }

    optionsTarget?.let { target ->
        OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
    }
}
