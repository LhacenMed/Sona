package com.lhacenmed.sona.feature.library.shuffle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.AlbumRow
import com.lhacenmed.sona.feature.library.ArtistRow
import com.lhacenmed.sona.feature.library.FolderRow
import com.lhacenmed.sona.feature.library.GenreRow
import com.lhacenmed.sona.feature.library.LibraryList
import com.lhacenmed.sona.feature.library.PlaylistRow
import com.lhacenmed.sona.feature.library.filterItems
import com.lhacenmed.sona.feature.library.matchesSearch
import com.lhacenmed.sona.feature.library.searchEmptyMessage

/**
 * Choosing which [kind] of collection shuffle-all plays - opened from the settings once the kind is
 * picked. The library's own rows, searchable, with the current source marked; a tap chooses that row and
 * closes the screen. A collection holding no tracks cannot be chosen, as it would give nothing to shuffle.
 */
data class ShuffleSourcePickerScreen(val kind: ShuffleSourceKind) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: ShuffleSourcePickerViewModel = hiltViewModel()
        val source by viewModel.source.collectAsStateWithLifecycle()
        var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
        val choose: (PlaybackParent) -> Unit = { parent ->
            viewModel.choose(parent)
            navigator.close()
        }
        val isSource: (PlaybackParent) -> Boolean = { it == source.parent }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = kind.pickerTitle,
                onNavigateBack = navigator::back,
                actions = listOf(TopBarAction(label = "Search", icon = Icons.Filled.Search) { searchQuery = "" }),
                search = searchQuery?.let { query ->
                    TopBarSearch(
                        query = query,
                        onQueryChange = { searchQuery = it },
                        onClose = { searchQuery = null },
                    )
                },
            )
            when (kind) {
                ShuffleSourceKind.PLAYLIST -> {
                    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                    PickerList(
                        content = playlists.filterItems { it.trackCount > 0 && matchesSearch(searchQuery, it.name) },
                        emptyTitle = "No playlists found",
                        searchQuery = searchQuery,
                        loadingIcon = kind.icon,
                        key = { it.id },
                    ) { playlist ->
                        val parent = PlaybackParent.Playlist(playlist.id)
                        PlaylistRow(
                            playlist = playlist,
                            selection = null,
                            isCurrent = { isSource(parent) },
                            isPlaying = { false },
                            onClick = { choose(parent) },
                            onOpenOptions = null,
                        )
                    }
                }

                ShuffleSourceKind.ARTIST -> {
                    val artists by viewModel.artists.collectAsStateWithLifecycle()
                    val sections by viewModel.artistSections.collectAsStateWithLifecycle()
                    PickerList(
                        content = artists.filterItems { it.trackCount > 0 && matchesSearch(searchQuery, it.name) },
                        emptyTitle = "No artists found",
                        searchQuery = searchQuery,
                        loadingIcon = kind.icon,
                        key = { it.id },
                        sectionOf = sections,
                    ) { artist ->
                        val parent = PlaybackParent.Artist(artist.id)
                        ArtistRow(
                            artist = artist,
                            selection = null,
                            isCurrent = { isSource(parent) },
                            isPlaying = { false },
                            onClick = { choose(parent) },
                            onOpenOptions = null,
                        )
                    }
                }

                ShuffleSourceKind.ALBUM -> {
                    val albums by viewModel.albums.collectAsStateWithLifecycle()
                    val sections by viewModel.albumSections.collectAsStateWithLifecycle()
                    PickerList(
                        content = albums.filterItems { matchesSearch(searchQuery, it.title, it.artistName) },
                        emptyTitle = "No albums found",
                        searchQuery = searchQuery,
                        loadingIcon = kind.icon,
                        key = { it.id },
                        sectionOf = sections,
                    ) { album ->
                        val parent = PlaybackParent.Album(album.id)
                        AlbumRow(
                            album = album,
                            selection = null,
                            isCurrent = { isSource(parent) },
                            isPlaying = { false },
                            onClick = { choose(parent) },
                            onOpenOptions = null,
                        )
                    }
                }

                ShuffleSourceKind.GENRE -> {
                    val genres by viewModel.genres.collectAsStateWithLifecycle()
                    val sections by viewModel.genreSections.collectAsStateWithLifecycle()
                    PickerList(
                        content = genres.filterItems { matchesSearch(searchQuery, it.name) },
                        emptyTitle = "No genres found",
                        searchQuery = searchQuery,
                        loadingIcon = kind.icon,
                        key = { it.id },
                        sectionOf = sections,
                    ) { genre ->
                        val parent = PlaybackParent.Genre(genre.id)
                        GenreRow(
                            genre = genre,
                            selection = null,
                            isCurrent = { isSource(parent) },
                            isPlaying = { false },
                            onClick = { choose(parent) },
                            onOpenOptions = null,
                        )
                    }
                }

                ShuffleSourceKind.FOLDER -> {
                    val folders by viewModel.folders.collectAsStateWithLifecycle()
                    val sections by viewModel.folderSections.collectAsStateWithLifecycle()
                    PickerList(
                        content = folders.filterItems { matchesSearch(searchQuery, it.name) },
                        emptyTitle = "No folders found",
                        searchQuery = searchQuery,
                        loadingIcon = kind.icon,
                        key = { it.path },
                        sectionOf = sections,
                    ) { folder ->
                        val parent = PlaybackParent.Folder(folder.path)
                        FolderRow(
                            folder = folder,
                            selection = null,
                            isCurrent = { isSource(parent) },
                            isPlaying = { false },
                            onClick = { choose(parent) },
                            onOpenOptions = null,
                        )
                    }
                }
            }
        }
    }
}

private val ShuffleSourceKind.pickerTitle: String
    get() = when (this) {
        ShuffleSourceKind.PLAYLIST -> "Choose a playlist"
        ShuffleSourceKind.ARTIST -> "Choose an artist"
        ShuffleSourceKind.ALBUM -> "Choose an album"
        ShuffleSourceKind.GENRE -> "Choose a genre"
        ShuffleSourceKind.FOLDER -> "Choose a folder"
    }

/**
 * A picker's list, reached from the settings - so the library has already loaded and been permitted.
 * Its rows take no selection: a tap is the whole choice.
 */
@Composable
private fun <T> PickerList(
    content: LibraryContent<T>,
    emptyTitle: String,
    searchQuery: String?,
    loadingIcon: ImageVector,
    key: (T) -> Any,
    sectionOf: ((T) -> String?)? = null,
    row: @Composable (T) -> Unit,
) {
    LibraryList(
        content = content,
        selection = rememberSelectionState(),
        hasPermission = true,
        isScanning = false,
        emptyTitle = emptyTitle,
        emptyMessage = searchEmptyMessage(searchQuery),
        key = key,
        loadingIcon = loadingIcon,
        modifier = Modifier.fillMaxSize(),
        sectionOf = sectionOf,
        row = row,
    )
}
