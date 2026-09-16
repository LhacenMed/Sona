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
fun AlbumsScreen(
    viewModel: LibraryViewModel,
    selection: SelectionState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LibraryList(
        content = albums,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No albums found",
        emptyMessage = "Add some music to your device to see it here.",
        key = { it.id },
        loadingIcon = SonaIcons.Album,
        modifier = modifier,
        listState = listState,
    ) { album ->
        AlbumRow(
            album = album,
            selection = selection,
            isCurrent = { playback.marks(album) },
            isPlaying = { playback.isPlaying },
            onClick = { navigator.go(AlbumDetailScreen(album.id)) },
        )
    }
}
