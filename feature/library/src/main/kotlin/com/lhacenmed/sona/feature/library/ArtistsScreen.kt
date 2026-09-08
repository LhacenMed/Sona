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
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.navigation.LocalNavigator

@Composable
fun ArtistsScreen(
    modifier: Modifier = Modifier,
    viewModel: ArtistsViewModel = hiltViewModel(),
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LibraryListContent(
        items = artists,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No artists found",
        emptyMessage = "Add some music to your device to see it here.",
        modifier = modifier,
    ) { loadedArtists ->
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(loadedArtists.size) { index ->
                val artist = loadedArtists[index]
                ArtistRow(
                    artist = artist,
                    onClick = { navigator.go(ArtistDetailScreen(artist.id)) },
                )
            }
        }
    }
}

@Composable
private fun ArtistRow(
    artist: Artist,
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
            text = artist.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "${artist.albumCount} albums · ${artist.trackCount} tracks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
