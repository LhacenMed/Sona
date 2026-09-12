package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
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
        val filter by viewModel.filter.collectAsStateWithLifecycle()
        val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                // The bar is only ever in its searching mode here: this screen has no other job, so
                // there is no title to return to and closing the field is closing the screen.
                title = "Search",
                search = TopBarSearch(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    onClose = navigator::back,
                    // Closing the search here is leaving the screen, which back already does.
                    closesWithBack = false,
                ),
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
            SearchFilterRow(selected = filter, onClick = viewModel::onFilterClick)

            when {
                query.isBlank() -> EmptyLibraryState(
                    title = "Search your library",
                    message = "Find tracks, albums, artists, and genres.",
                )

                uiState.isEmpty -> EmptyLibraryState(
                    title = "No results",
                    message = filter?.let { "No ${it.label.lowercase()} matched \"$query\"." }
                        ?: "Nothing matched \"$query\".",
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // A filter already names the one kind of result on screen, so repeating it as a
                    // heading above the only section would say nothing the chip has not.
                    val showHeaders = filter == null
                    if (uiState.tracks.isNotEmpty()) {
                        if (showHeaders) item { SearchSectionHeader("Tracks") }
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
                        if (showHeaders) item { SearchSectionHeader("Albums") }
                        items(uiState.albums, key = { "album-${it.id}" }) { album ->
                            SearchResultRow(
                                title = album.title,
                                subtitle = album.artistName,
                                onClick = { navigator.go(AlbumDetailScreen(album.id)) },
                            )
                        }
                    }
                    if (uiState.artists.isNotEmpty()) {
                        if (showHeaders) item { SearchSectionHeader("Artists") }
                        items(uiState.artists, key = { "artist-${it.id}" }) { artist ->
                            SearchResultRow(
                                title = artist.name,
                                subtitle = "${artist.trackCount} tracks",
                                onClick = { navigator.go(ArtistDetailScreen(artist.id)) },
                            )
                        }
                    }
                    if (uiState.genres.isNotEmpty()) {
                        if (showHeaders) item { SearchSectionHeader("Genres") }
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

/**
 * The row of filters under the bar.
 *
 * Scrollable rather than wrapping, so that adding a fifth kind of result lengthens the row instead
 * of pushing the results down a line - the height under the bar has to stay put while the user
 * types. The padding sits inside the scroll so the first chip starts at the screen margin and then
 * scrolls past it, the way a list's content padding behaves.
 */
@Composable
private fun SearchFilterRow(
    selected: SearchFilter?,
    onClick: (SearchFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onClick(filter) },
                label = { Text(filter.label) },
            )
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
