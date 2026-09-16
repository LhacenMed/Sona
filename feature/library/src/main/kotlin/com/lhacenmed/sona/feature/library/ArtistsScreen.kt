package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.LocalNavigator

@Composable
fun ArtistsScreen(
    viewModel: LibraryViewModel,
    selection: SelectionState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LibraryList(
        content = artists,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No artists found",
        emptyMessage = "Add some music to your device to see it here.",
        key = { it.id },
        loadingIcon = SonaIcons.Artist,
        modifier = modifier,
        listState = listState,
    ) { artist ->
        ArtistRow(
            artist = artist,
            selection = selection,
            isCurrent = { playback.marks(artist) },
            isPlaying = { playback.isPlaying },
            onClick = { navigator.go(ArtistDetailScreen(artist.id)) },
        )
    }
}
