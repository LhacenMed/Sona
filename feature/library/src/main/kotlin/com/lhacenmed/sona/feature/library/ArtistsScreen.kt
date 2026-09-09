package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.navigation.LocalNavigator

@Composable
fun ArtistsScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LibraryList(
        content = artists,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No artists found",
        emptyMessage = "Add some music to your device to see it here.",
        key = { it.id },
        modifier = modifier,
    ) { artist ->
        LibraryEntityRow(
            title = artist.name,
            subtitle = "${artist.albumCount} albums · ${artist.trackCount} tracks",
            onClick = { navigator.go(ArtistDetailScreen(artist.id)) },
        )
    }
}
