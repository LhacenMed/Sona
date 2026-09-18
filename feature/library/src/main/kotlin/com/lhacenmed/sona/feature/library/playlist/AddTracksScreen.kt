package com.lhacenmed.sona.feature.library.playlist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.LibraryList
import com.lhacenmed.sona.feature.library.TrackRow
import com.lhacenmed.sona.feature.library.filterItems
import com.lhacenmed.sona.feature.library.selection.SelectionKey

/** Picking tracks one by one from the whole library, to add to the playlist [playlistId]. */
data class AddTracksScreen(val playlistId: Long) : Screen {

    @Composable
    override fun Content() {
        val viewModel: PlaylistPickerViewModel = hiltViewModel()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()
        var searchQuery by remember { mutableStateOf<String?>(null) }
        val visibleTracks = tracks.filterItems { matchesSearch(searchQuery, it.title, it.artist) }

        PlaylistTrackPicker(
            title = "Add tracks",
            playlistId = playlistId,
            selection = selection,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            visibleKeys = { visibleTracks.itemsOrEmpty.map { SelectionKey.Track(it.id) } },
        ) {
            LibraryList(
                content = visibleTracks,
                // Reached from a playlist, so the library has already loaded and been permitted.
                hasPermission = true,
                isScanning = false,
                emptyTitle = "No tracks found",
                emptyMessage = pickerEmptyMessage(searchQuery),
                key = { it.id },
                loadingIcon = SonaIcons.Song,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { track ->
                TrackRow(
                    track = track,
                    isCurrent = { false },
                    isPlaying = { false },
                    selection = selection,
                    onClick = selection.pickerClick(SelectionKey.Track(track.id)),
                    onOpenOptions = null,
                )
            }
        }
    }
}
