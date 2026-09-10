package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
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

@HiltViewModel(assistedFactory = PlaylistDetailViewModel.Factory::class)
class PlaylistDetailViewModel @AssistedInject constructor(
    @Assisted playlistId: Long,
    repository: LibraryRepository,
    playbackController: PlaybackController,
) : TrackListDetailViewModel(playbackController) {

    @AssistedFactory
    interface Factory {
        fun create(playlistId: Long): PlaylistDetailViewModel
    }

    val playlist: StateFlow<Playlist?> = repository.playlists
        .map { content -> content.itemsOrEmpty.firstOrNull { it.id == playlistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override val tracks: StateFlow<LibraryContent<Track>> = repository.playlistTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent.Loading)
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    repository: LibraryRepository,
    playbackController: PlaybackController,
) : TrackListDetailViewModel(playbackController) {

    override val tracks: StateFlow<LibraryContent<Track>> = repository.favoriteTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent.Loading)
}

@HiltViewModel
class RecentlyPlayedViewModel @Inject constructor(
    repository: LibraryRepository,
    playbackController: PlaybackController,
) : TrackListDetailViewModel(playbackController) {

    override val tracks: StateFlow<LibraryContent<Track>> = repository.recentlyPlayedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent.Loading)
}

@HiltViewModel
class MostPlayedViewModel @Inject constructor(
    repository: LibraryRepository,
    playbackController: PlaybackController,
) : TrackListDetailViewModel(playbackController) {

    override val tracks: StateFlow<LibraryContent<Track>> = repository.mostPlayedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent.Loading)
}
