package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

object SearchScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: SearchViewModel = hiltViewModel()
        val query by viewModel.query.collectAsStateWithLifecycle()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = navigator::back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    placeholder = { Text("Search your library") },
                    singleLine = true,
                )
            }

            when {
                query.isBlank() -> EmptyLibraryState(
                    title = "Search your library",
                    message = "Find tracks, albums, artists, and genres.",
                )

                uiState.isEmpty -> EmptyLibraryState(
                    title = "No results",
                    message = "Nothing matched \"$query\".",
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (uiState.tracks.isNotEmpty()) {
                        item { SearchSectionHeader("Tracks") }
                        items(uiState.tracks) { track ->
                            DetailTrackRow(
                                track = track,
                                isPlaying = track.id == playbackState.currentTrackId,
                                onClick = { viewModel.onTrackClick(track) },
                            )
                        }
                    }
                    if (uiState.albums.isNotEmpty()) {
                        item { SearchSectionHeader("Albums") }
                        items(uiState.albums) { album ->
                            SearchResultRow(
                                title = album.title,
                                subtitle = album.artistName,
                                onClick = { navigator.go(AlbumDetailScreen(album.id)) },
                            )
                        }
                    }
                    if (uiState.artists.isNotEmpty()) {
                        item { SearchSectionHeader("Artists") }
                        items(uiState.artists) { artist ->
                            SearchResultRow(
                                title = artist.name,
                                subtitle = "${artist.trackCount} tracks",
                                onClick = { navigator.go(ArtistDetailScreen(artist.id)) },
                            )
                        }
                    }
                    if (uiState.genres.isNotEmpty()) {
                        item { SearchSectionHeader("Genres") }
                        items(uiState.genres) { genre ->
                            SearchResultRow(
                                title = genre.name,
                                subtitle = "${genre.trackCount} tracks",
                                onClick = { navigator.go(GenreDetailScreen(genre.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
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
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
