package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.navigation.LocalNavigator

@Composable
fun AlbumsScreen(
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LibraryListContent(
        items = albums,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No albums found",
        emptyMessage = "Add some music to your device to see it here.",
        modifier = modifier,
    ) { loadedAlbums ->
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(loadedAlbums.size) { index ->
                val album = loadedAlbums[index]
                AlbumRow(
                    album = album,
                    onClick = { navigator.go(AlbumDetailScreen(album.id)) },
                )
            }
        }
    }
}

@Composable
private fun AlbumRow(
    album: Album,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "${album.artistName} · ${album.trackCount} tracks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
