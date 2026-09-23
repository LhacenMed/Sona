package com.lhacenmed.sona.feature.library.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.LibraryPlayback
import com.lhacenmed.sona.feature.library.libraryPlayback
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.selection.tracksOf
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the screens that pick tracks for a playlist read and do: the whole library, already computed
 * once for the process, played and marked as the library's own lists are, and adding what was picked.
 */
@HiltViewModel
class PlaylistPickerViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    val tracks: StateFlow<LibraryContent<Track>> = repository.tracks
    val albums: StateFlow<LibraryContent<Album>> = repository.albums
    val artists: StateFlow<LibraryContent<Artist>> = repository.artists
    val genres: StateFlow<LibraryContent<Genre>> = repository.genres
    val folders: StateFlow<LibraryContent<Folder>> = repository.folders
    val playlists: StateFlow<LibraryContent<Playlist>> = repository.playlists

    /** The section each list's rows sit in under its library tab's sort - what the fast scroller's popup names. */
    val trackSections: StateFlow<(Track) -> String?> = repository.trackSections
    val albumSections: StateFlow<(Album) -> String?> = repository.albumSections
    val artistSections: StateFlow<(Artist) -> String?> = repository.artistSections
    val genreSections: StateFlow<(Genre) -> String?> = repository.genreSections
    val folderSections: StateFlow<(Folder) -> String?> = repository.folderSections

    /** What the rows mark as playing - see [LibraryPlayback]. */
    val playback: StateFlow<LibraryPlayback> = libraryPlayback(playbackController, repository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPlayback())

    /**
     * Plays [shownTracks], the tracks on screen, from [track] - as the library's Tracks tab does, pausing
     * or resuming instead when it is already playing from there. See [LibraryPlayback.isReselection].
     */
    fun onTrackClick(track: Track, shownTracks: List<Track>) {
        if (playback.value.isReselection(track, listParent = null)) {
            playbackController.togglePlayPause()
            return
        }
        val index = shownTracks.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playTracks(shownTracks, index)
    }

    /**
     * The tracks [keys] would really add to [playlistId]: in the order they were picked, each once, and
     * none it already holds - exactly what the playlist keeps of an addition, so the confirmation counts
     * what will actually change.
     */
    fun resolveAddition(playlistId: Long, keys: List<SelectionKey>, onResolved: (trackIds: List<Long>) -> Unit) {
        viewModelScope.launch {
            val alreadyAdded = repository.tracksOf(listOf(SelectionKey.Playlist(playlistId))).mapTo(HashSet()) { it.id }
            onResolved(repository.tracksOf(keys).map { it.id }.distinct().filterNot { it in alreadyAdded })
        }
    }

    /**
     * Appends [trackIds] to [playlistId] and reports how it went - waited on, so the screen only leaves
     * once the tracks are really in the playlist.
     */
    fun addToPlaylist(playlistId: Long, trackIds: List<Long>, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) { repository.addTracksToPlaylist(playlistId, trackIds) }
    }
}
