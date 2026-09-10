package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

object SearchScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: SearchViewModel = hiltViewModel()
        val query by viewModel.query.collectAsStateWithLifecycle()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = "Search",
                onNavigateBack = navigator::back,
                selection = selection.toTopBarSelection(
                    actions = listOf(
                        TopBarAction(label = "Play", icon = Icons.Filled.PlayArrow) {
                            viewModel.playSelection(selection.selectedKeys)
                            selection.clear()
                        },
                        TopBarAction(label = "Select all", icon = Icons.Filled.SelectAll) {
                            selection.selectAll(viewModel.selectableKeys())
                        },
                    ),
                ),
            )
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search your library") },
                singleLine = true,
            )

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
                        items(uiState.tracks, key = { "track-${it.id}" }) { track ->
                            TrackRow(
                                track = track,
                                isPlaying = { track.id == currentTrackId },
                                selection = selection,
                                onClick = { viewModel.onTrackClick(track) },
                            )
                        }
                    }
                    if (uiState.albums.isNotEmpty()) {
                        item { SearchSectionHeader("Albums") }
                        items(uiState.albums, key = { "album-${it.id}" }) { album ->
                            SearchResultRow(
                                title = album.title,
                                subtitle = album.artistName,
                                onClick = { navigator.go(AlbumDetailScreen(album.id)) },
                            )
                        }
                    }
                    if (uiState.artists.isNotEmpty()) {
                        item { SearchSectionHeader("Artists") }
                        items(uiState.artists, key = { "artist-${it.id}" }) { artist ->
                            SearchResultRow(
                                title = artist.name,
                                subtitle = "${artist.trackCount} tracks",
                                onClick = { navigator.go(ArtistDetailScreen(artist.id)) },
                            )
                        }
                    }
                    if (uiState.genres.isNotEmpty()) {
                        item { SearchSectionHeader("Genres") }
                        items(uiState.genres, key = { "genre-${it.id}" }) { genre ->
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
