package com.lhacenmed.sona.feature.library.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.PlaylistCover
import com.lhacenmed.sona.core.model.Track
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The playlist [playlistId]'s name and cover, as a draft that reaches the playlist only on [save].
 *
 * The draft is snapshot state rather than a flow, so the name field is updated in the same frame it is
 * typed in. A track or an image once chosen is kept while another cover is tried, so going back to it
 * needs no second pick.
 */
@HiltViewModel(assistedFactory = EditPlaylistViewModel.Factory::class)
class EditPlaylistViewModel @AssistedInject constructor(
    @Assisted private val playlistId: Long,
    private val repository: LibraryRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(playlistId: Long): EditPlaylistViewModel
    }

    val playlist: StateFlow<Playlist?> = repository.playlists
        .map { content -> content.itemsOrEmpty.firstOrNull { it.id == playlistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every other playlist's name, none of which this one can take. */
    val takenNames: StateFlow<List<String>> = repository.playlists
        .map { content -> content.itemsOrEmpty.filter { it.id != playlistId }.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The playlist's tracks in its current sort - which its first and last track are counted in. */
    val playlistTracks: StateFlow<List<Track>> = repository.playlistTracks(playlistId)
        .map { it.itemsOrEmpty }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryTracks: StateFlow<LibraryContent<Track>> = repository.tracks

    val tracksById: StateFlow<Map<Long, Track>> = repository.tracksById

    /** False until the draft has been filled in from the playlist. */
    var isDraftReady by mutableStateOf(false)
        private set

    var name by mutableStateOf("")
        private set

    var cover by mutableStateOf<PlaylistCover>(PlaylistCover.Stacked)
        private set

    var chosenTrackId by mutableStateOf<Long?>(null)
        private set

    var chosenImageUri by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val saved = playlist.filterNotNull().first()
            name = saved.name
            cover = saved.cover
            chosenTrackId = (saved.cover as? PlaylistCover.OfTrack)?.trackId
            chosenImageUri = (saved.cover as? PlaylistCover.Image)?.uri
            isDraftReady = true
        }
    }

    fun rename(name: String) {
        this.name = name
    }

    fun selectCover(cover: PlaylistCover) {
        this.cover = cover
    }

    fun chooseTrack(track: Track) {
        chosenTrackId = track.id
        cover = PlaylistCover.OfTrack(track.id)
    }

    fun chooseImage(uri: String) {
        chosenImageUri = uri
        cover = PlaylistCover.Image(uri)
    }

    fun save(onFinished: (succeeded: Boolean) -> Unit) {
        val name = name
        val cover = cover
        viewModelScope.launchOperation(onFinished) { repository.editPlaylist(playlistId, name, cover) }
    }
}
