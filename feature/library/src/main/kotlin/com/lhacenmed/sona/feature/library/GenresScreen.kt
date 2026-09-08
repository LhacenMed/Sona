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
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.navigation.LocalNavigator

@Composable
fun GenresScreen(
    modifier: Modifier = Modifier,
    viewModel: GenresViewModel = hiltViewModel(),
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LibraryListContent(
        items = genres,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No genres found",
        emptyMessage = "Add some music to your device to see it here.",
        modifier = modifier,
    ) { loadedGenres ->
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(loadedGenres.size) { index ->
                val genre = loadedGenres[index]
                GenreRow(
                    genre = genre,
                    onClick = { navigator.go(GenreDetailScreen(genre.id)) },
                )
            }
        }
    }
}

@Composable
private fun GenreRow(
    genre: Genre,
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
            text = genre.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "${genre.trackCount} tracks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
