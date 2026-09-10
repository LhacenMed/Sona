package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.navigation.LocalNavigator

@Composable
fun AlbumsScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LibraryList(
        content = albums,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No albums found",
        emptyMessage = "Add some music to your device to see it here.",
        key = { it.id },
        modifier = modifier,
    ) { album ->
        LibraryEntityRow(
            title = album.title,
            subtitle = "${album.artistName} · ${album.trackCount} tracks",
            onClick = { navigator.go(AlbumDetailScreen(album.id)) },
        )
    }
}
