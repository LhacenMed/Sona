package com.lhacenmed.sona.feature.library.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.operation.launchOperation
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.selection.tracksOf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * What the screens that pick tracks for a playlist read and do: the whole library, already computed
 * once for the process, and adding what was picked.
 */
@HiltViewModel
class PlaylistPickerViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {

    val tracks: StateFlow<LibraryContent<Track>> = repository.tracks
    val albums: StateFlow<LibraryContent<Album>> = repository.albums
    val artists: StateFlow<LibraryContent<Artist>> = repository.artists
    val genres: StateFlow<LibraryContent<Genre>> = repository.genres
    val folders: StateFlow<LibraryContent<Folder>> = repository.folders
    val playlists: StateFlow<LibraryContent<Playlist>> = repository.playlists

    /**
     * Adds the tracks [keys] stand for to [playlistId], in the order they were picked, and reports how it
     * went - waited on, so the screen only leaves once the tracks are really in the playlist.
     */
    fun addToPlaylist(playlistId: Long, keys: List<SelectionKey>, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) {
            repository.addTracksToPlaylist(playlistId, repository.tracksOf(keys).map { it.id })
        }
    }
}
