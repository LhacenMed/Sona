package com.lhacenmed.sona.feature.library.playlist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaTabRow
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.AlbumDetailScreen
import com.lhacenmed.sona.feature.library.AlbumRow
import com.lhacenmed.sona.feature.library.ArtistDetailScreen
import com.lhacenmed.sona.feature.library.ArtistRow
import com.lhacenmed.sona.feature.library.FolderDetailScreen
import com.lhacenmed.sona.feature.library.FolderRow
import com.lhacenmed.sona.feature.library.GenreDetailScreen
import com.lhacenmed.sona.feature.library.GenreRow
import com.lhacenmed.sona.feature.library.LibraryList
import com.lhacenmed.sona.feature.library.LibraryPlayback
import com.lhacenmed.sona.feature.library.PlaylistDetailScreen
import com.lhacenmed.sona.feature.library.PlaylistRow
import com.lhacenmed.sona.feature.library.filterItems
import com.lhacenmed.sona.feature.library.matchesSearch
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.searchEmptyMessage
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import kotlinx.coroutines.launch

/** The kinds of collection a playlist can take tracks from, in the library's own tab order. */
private enum class CollectionTab(val label: String) {
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    GENRES("Genres"),
    FOLDERS("Folders"),
    PLAYLISTS("Playlists"),
}

/** Every tab's rows as they stand on screen - narrowed by the search, and without the playlist being added to. */
private class VisibleCollections(
    val artists: LibraryContent<Artist>,
    val albums: LibraryContent<Album>,
    val genres: LibraryContent<Genre>,
    val folders: LibraryContent<Folder>,
    val playlists: LibraryContent<Playlist>,
    /** The section each tab's rows sit in, as their library tab sorts them - what its fast scroller names. */
    val artistSections: (Artist) -> String?,
    val albumSections: (Album) -> String?,
    val genreSections: (Genre) -> String?,
    val folderSections: (Folder) -> String?,
) {
    /** The keys of [tab]'s rows that can be picked - what its Select all selects. */
    fun selectableKeys(tab: CollectionTab): List<SelectionKey> = when (tab) {
        CollectionTab.ARTISTS -> artists.itemsOrEmpty.filter { it.trackCount > 0 }.map { SelectionKey.Artist(it.id) }
        CollectionTab.ALBUMS -> albums.itemsOrEmpty.map { SelectionKey.Album(it.id) }
        CollectionTab.GENRES -> genres.itemsOrEmpty.map { SelectionKey.Genre(it.id) }
        CollectionTab.FOLDERS -> folders.itemsOrEmpty.map { SelectionKey.Folder(it.path) }
        CollectionTab.PLAYLISTS -> playlists.itemsOrEmpty.filter { it.trackCount > 0 }.map { SelectionKey.Playlist(it.id) }
    }
}

/**
 * Picking whole collections - artists, albums, genres, folders and other playlists - to add every one
 * of their tracks to the playlist [playlistId].
 *
 * One selection runs across every tab, so an album and a genre can be gathered in a single add, and one
 * search narrows every tab by name. Select all works on the tab on screen. The playlist being added to
 * is not offered, and a collection holding no tracks cannot be picked.
 */
data class AddCollectionsScreen(val playlistId: Long) : Screen {

    @Composable
    override fun Content() {
        val viewModel: PlaylistPickerViewModel = hiltViewModel()
        val artists by viewModel.artists.collectAsStateWithLifecycle()
        val albums by viewModel.albums.collectAsStateWithLifecycle()
        val genres by viewModel.genres.collectAsStateWithLifecycle()
        val folders by viewModel.folders.collectAsStateWithLifecycle()
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        val playback by viewModel.playback.collectAsStateWithLifecycle()
        val artistSections by viewModel.artistSections.collectAsStateWithLifecycle()
        val albumSections by viewModel.albumSections.collectAsStateWithLifecycle()
        val genreSections by viewModel.genreSections.collectAsStateWithLifecycle()
        val folderSections by viewModel.folderSections.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()
        var searchQuery by remember { mutableStateOf<String?>(null) }
        val pagerState = rememberPagerState(pageCount = { CollectionTab.entries.size })
        val scope = rememberCoroutineScope()
        var optionsTarget by remember { mutableStateOf<OptionsTarget?>(null) }

        val visible = VisibleCollections(
            artists = artists.filterItems { matchesSearch(searchQuery, it.name) },
            albums = albums.filterItems { matchesSearch(searchQuery, it.title, it.artistName) },
            genres = genres.filterItems { matchesSearch(searchQuery, it.name) },
            folders = folders.filterItems { matchesSearch(searchQuery, it.name) },
            playlists = playlists.filterItems { it.id != playlistId && matchesSearch(searchQuery, it.name) },
            artistSections = artistSections,
            albumSections = albumSections,
            genreSections = genreSections,
            folderSections = folderSections,
        )

        PlaylistTrackPicker(
            title = "Add from collections",
            playlistId = playlistId,
            selection = selection,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            visibleKeys = { visible.selectableKeys(CollectionTab.entries[pagerState.currentPage]) },
        ) {
            SonaTabRow(
                tabTitles = CollectionTab.entries.map { it.label },
                selectedPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                onTabClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.padding(horizontal = SonaComponentStyle.ContentHorizontalPadding, vertical = 8.dp),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                // Every tab kept composed, so a list keeps its place while another tab is on screen.
                beyondViewportPageCount = CollectionTab.entries.size - 1,
            ) { page ->
                CollectionPage(
                    tab = CollectionTab.entries[page],
                    visible = visible,
                    selection = selection,
                    playback = { playback },
                    emptyMessage = searchEmptyMessage(searchQuery),
                    onOpenOptions = { optionsTarget = it },
                )
            }
        }

        optionsTarget?.let { target ->
            OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
        }
    }
}

@Composable
private fun CollectionPage(
    tab: CollectionTab,
    visible: VisibleCollections,
    selection: SelectionState,
    playback: () -> LibraryPlayback,
    emptyMessage: String,
    onOpenOptions: (OptionsTarget) -> Unit,
) {
    val navigator = LocalNavigator.current
    when (tab) {
        CollectionTab.ARTISTS -> PickerList(visible.artists, "No artists found", emptyMessage, key = { it.id }, visible.artistSections) { artist ->
            ArtistRow(
                artist = artist,
                selection = selection,
                isCurrent = { playback().marks(artist) },
                isPlaying = { playback().isPlaying },
                onClick = { navigator.go(ArtistDetailScreen(artist.id)) },
                onOpenOptions = { onOpenOptions(OptionsTarget.ForArtist(artist)) },
            )
        }

        CollectionTab.ALBUMS -> PickerList(visible.albums, "No albums found", emptyMessage, key = { it.id }, visible.albumSections) { album ->
            AlbumRow(
                album = album,
                selection = selection,
                isCurrent = { playback().marks(album) },
                isPlaying = { playback().isPlaying },
                onClick = { navigator.go(AlbumDetailScreen(album.id)) },
                onOpenOptions = { onOpenOptions(OptionsTarget.ForAlbum(album)) },
            )
        }

        CollectionTab.GENRES -> PickerList(visible.genres, "No genres found", emptyMessage, key = { it.id }, visible.genreSections) { genre ->
            GenreRow(
                genre = genre,
                selection = selection,
                isCurrent = { playback().marks(genre) },
                isPlaying = { playback().isPlaying },
                onClick = { navigator.go(GenreDetailScreen(genre.id)) },
                onOpenOptions = { onOpenOptions(OptionsTarget.ForGenre(genre)) },
            )
        }

        CollectionTab.FOLDERS -> PickerList(visible.folders, "No folders found", emptyMessage, key = { it.path }, visible.folderSections) { folder ->
            FolderRow(
                folder = folder,
                selection = selection,
                isCurrent = { playback().marks(folder) },
                isPlaying = { playback().isPlaying },
                onClick = { navigator.go(FolderDetailScreen(folder.path)) },
                onOpenOptions = { onOpenOptions(OptionsTarget.ForFolder(folder)) },
            )
        }

        CollectionTab.PLAYLISTS -> PickerList(visible.playlists, "No other playlists", emptyMessage, key = { it.id }) { playlist ->
            PlaylistRow(
                playlist = playlist,
                selection = selection,
                isCurrent = { playback().marks(playlist) },
                isPlaying = { playback().isPlaying },
                onClick = { navigator.go(PlaylistDetailScreen(playlist.id)) },
                onOpenOptions = { onOpenOptions(OptionsTarget.ForPlaylist(playlist)) },
            )
        }
    }
}

/** A picker tab's list, reached from a playlist - so the library has already loaded and been permitted. */
@Composable
private fun <T> PickerList(
    content: LibraryContent<T>,
    emptyTitle: String,
    emptyMessage: String,
    key: (T) -> Any,
    sectionOf: ((T) -> String?)? = null,
    row: @Composable (T) -> Unit,
) {
    LibraryList(
        content = content,
        hasPermission = true,
        isScanning = false,
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        key = key,
        loadingIcon = SonaIcons.Playlist,
        modifier = Modifier.fillMaxSize(),
        sectionOf = sectionOf,
        row = row,
    )
}
