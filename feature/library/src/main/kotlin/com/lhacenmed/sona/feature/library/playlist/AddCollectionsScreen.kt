package com.lhacenmed.sona.feature.library.playlist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaTabRow
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.AlbumRow
import com.lhacenmed.sona.feature.library.ArtistRow
import com.lhacenmed.sona.feature.library.FolderRow
import com.lhacenmed.sona.feature.library.GenreRow
import com.lhacenmed.sona.feature.library.LibraryList
import com.lhacenmed.sona.feature.library.PlaylistRow
import com.lhacenmed.sona.feature.library.filterItems
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

/**
 * Picking whole collections - artists, albums, genres, folders and other playlists - to add every one
 * of their tracks to the playlist [playlistId].
 *
 * One selection runs across every tab, so an album and a genre can be gathered in a single add. The
 * playlist being added to is not offered, and neither is a collection holding no tracks.
 */
data class AddCollectionsScreen(val playlistId: Long) : Screen {

    @Composable
    override fun Content() {
        PlaylistTrackPicker(title = "Add from collections", playlistId = playlistId) { selection ->
            val pagerState = rememberPagerState(pageCount = { CollectionTab.entries.size })
            val scope = rememberCoroutineScope()

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
                CollectionPage(tab = CollectionTab.entries[page], playlistId = playlistId, selection = selection)
            }
        }
    }
}

@Composable
private fun CollectionPage(tab: CollectionTab, playlistId: Long, selection: SelectionState) {
    val viewModel: PlaylistPickerViewModel = hiltViewModel()
    val modifier = Modifier.fillMaxSize()
    when (tab) {
        CollectionTab.ARTISTS -> {
            val artists by viewModel.artists.collectAsStateWithLifecycle()
            PickerList(content = artists, emptyTitle = "No artists found", key = { it.id }, modifier = modifier) { artist ->
                ArtistRow(
                    artist = artist,
                    selection = selection,
                    isCurrent = { false },
                    isPlaying = { false },
                    onClick = selection.pickerClick(SelectionKey.Artist(artist.id).takeIf { artist.trackCount > 0 }),
                    onOpenOptions = null,
                )
            }
        }

        CollectionTab.ALBUMS -> {
            val albums by viewModel.albums.collectAsStateWithLifecycle()
            PickerList(content = albums, emptyTitle = "No albums found", key = { it.id }, modifier = modifier) { album ->
                AlbumRow(
                    album = album,
                    selection = selection,
                    isCurrent = { false },
                    isPlaying = { false },
                    onClick = selection.pickerClick(SelectionKey.Album(album.id)),
                    onOpenOptions = null,
                )
            }
        }

        CollectionTab.GENRES -> {
            val genres by viewModel.genres.collectAsStateWithLifecycle()
            PickerList(content = genres, emptyTitle = "No genres found", key = { it.id }, modifier = modifier) { genre ->
                GenreRow(
                    genre = genre,
                    selection = selection,
                    isCurrent = { false },
                    isPlaying = { false },
                    onClick = selection.pickerClick(SelectionKey.Genre(genre.id)),
                    onOpenOptions = null,
                )
            }
        }

        CollectionTab.FOLDERS -> {
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            PickerList(content = folders, emptyTitle = "No folders found", key = { it.path }, modifier = modifier) { folder ->
                FolderRow(
                    folder = folder,
                    selection = selection,
                    isCurrent = { false },
                    isPlaying = { false },
                    onClick = selection.pickerClick(SelectionKey.Folder(folder.path)),
                    onOpenOptions = null,
                )
            }
        }

        CollectionTab.PLAYLISTS -> {
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            PickerList(
                content = playlists.filterItems { it.id != playlistId },
                emptyTitle = "No other playlists",
                key = { it.id },
                modifier = modifier,
            ) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    selection = selection,
                    isCurrent = { false },
                    isPlaying = { false },
                    onClick = selection.pickerClick(SelectionKey.Playlist(playlist.id).takeIf { playlist.trackCount > 0 }),
                    onOpenOptions = null,
                )
            }
        }
    }
}

/** A picker tab's list, reached from a playlist - so the library has already loaded and been permitted. */
@Composable
private fun <T> PickerList(
    content: LibraryContent<T>,
    emptyTitle: String,
    key: (T) -> Any,
    modifier: Modifier,
    row: @Composable (T) -> Unit,
) {
    LibraryList(
        content = content,
        hasPermission = true,
        isScanning = false,
        emptyTitle = emptyTitle,
        emptyMessage = "Add some music to your device to see it here.",
        key = key,
        loadingIcon = SonaIcons.Playlist,
        modifier = modifier,
        row = row,
    )
}
