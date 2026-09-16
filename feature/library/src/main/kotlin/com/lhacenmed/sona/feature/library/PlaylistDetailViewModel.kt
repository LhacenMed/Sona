package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.sort.SortableList
import com.lhacenmed.sona.feature.library.sort.SortControl
import com.lhacenmed.sona.feature.library.sort.control
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlaylistDetailViewModel.Factory::class)
class PlaylistDetailViewModel @AssistedInject constructor(
    @Assisted private val playlistId: Long,
    private val repository: LibraryRepository,
    sortOrders: LibrarySortOrders,
    playbackController: PlaybackController,
) : TrackListDetailViewModel(playbackController, repository) {

    override val sort: SortControl = sortOrders.control(SortableList.PLAYLIST_TRACKS, playlistId.toString())

    @AssistedFactory
    interface Factory {
        fun create(playlistId: Long): PlaylistDetailViewModel
    }

    override val playbackParent: PlaybackParent = PlaybackParent.Playlist(playlistId)

    val playlist: StateFlow<Playlist?> = repository.playlists
        .map { content -> content.itemsOrEmpty.firstOrNull { it.id == playlistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override val tracks: StateFlow<LibraryContent<Track>> = repository.playlistTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent.Loading)

    /**
     * Writes the order a drag ended on and puts the list in it. Called once on drop, never while the
     * finger moves.
     *
     * Arranging by hand is not a sort to be chosen - it is what dragging leaves behind - so the list
     * is switched into its arranged order here instead of the user having to ask for it. The order is
     * stored first, so the switch can only ever land on the arrangement this drag just made.
     */
    fun setOrder(trackIds: List<Long>) {
        viewModelScope.launch {
            repository.setPlaylistOrder(playlistId, trackIds)
            sort.applyArrangedOrder()
        }
    }

    /** Drops the selected tracks out of this playlist. The files themselves are untouched. */
    fun removeFromPlaylist(selectedKeys: Set<Any>) {
        val trackIds = selectedKeys.filterIsInstance<Long>()
        if (trackIds.isEmpty()) return
        viewModelScope.launch { repository.removeTracksFromPlaylist(playlistId, trackIds) }
    }

    /** Adds the one track that lives at [path], if the library knows it. */
    fun addFile(path: String) {
        val track = repository.tracks.value.itemsOrEmpty.firstOrNull { it.path == path } ?: return
        viewModelScope.launch { repository.addTracksToPlaylist(playlistId, listOf(track.id)) }
    }

    /**
     * Adds everything the library holds beneath [folderPath].
     *
     * Filtered from the list already in memory rather than walking the filesystem as the reference
     * app does: the last scan already found these, so the answer needs no I/O.
     */
    fun addFolder(folderPath: String) {
        val trackIds = repository.tracks.value.itemsOrEmpty
            .filter { it.folderPath == folderPath || it.folderPath.startsWith("$folderPath/") }
            .map { it.id }
        if (trackIds.isEmpty()) return
        viewModelScope.launch { repository.addTracksToPlaylist(playlistId, trackIds) }
    }
}

@HiltViewModel
class RecentlyPlayedViewModel @Inject constructor(
    repository: LibraryRepository,
    playbackController: PlaybackController,
) : TrackListDetailViewModel(playbackController, repository) {

    override val playbackParent: PlaybackParent = PlaybackParent.RecentlyPlayed

    override val tracks: StateFlow<LibraryContent<Track>> = repository.recentlyPlayedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent.Loading)
}

@HiltViewModel
class MostPlayedViewModel @Inject constructor(
    repository: LibraryRepository,
    playbackController: PlaybackController,
) : TrackListDetailViewModel(playbackController, repository) {

    override val playbackParent: PlaybackParent = PlaybackParent.MostPlayed

    override val tracks: StateFlow<LibraryContent<Track>> = repository.mostPlayedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent.Loading)
}
