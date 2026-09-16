package com.lhacenmed.sona.feature.library.playlist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.LibraryList
import com.lhacenmed.sona.feature.library.TrackRow
import com.lhacenmed.sona.feature.library.selection.SelectionKey

/** Picking tracks one by one from the whole library, to add to the playlist [playlistId]. */
data class AddTracksScreen(val playlistId: Long) : Screen {

    @Composable
    override fun Content() {
        val viewModel: PlaylistPickerViewModel = hiltViewModel()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        PlaylistTrackPicker(title = "Add tracks", playlistId = playlistId) { selection ->
            LibraryList(
                content = tracks,
                // Reached from a playlist, so the library has already loaded and been permitted.
                hasPermission = true,
                isScanning = false,
                emptyTitle = "No tracks found",
                emptyMessage = "Add some music to your device to see it here.",
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
