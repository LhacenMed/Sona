package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AlbumDetailUiState(
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
)

@HiltViewModel(assistedFactory = AlbumDetailViewModel.Factory::class)
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted private val albumId: Long,
    trackDao: TrackDao,
    albumDao: AlbumDao,
    private val playbackController: PlaybackController,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(albumId: Long): AlbumDetailViewModel
    }

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        albumDao.observeAll()
            .map { entities -> entities.map { it.toDomain() }.find { it.id == albumId } },
        trackDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
                .filter { it.albumId == albumId }
                .sortedWith(compareBy({ it.trackNumber ?: Int.MAX_VALUE }, { it.title }))
        },
    ) { album, tracks -> AlbumDetailUiState(album = album, tracks = tracks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumDetailUiState())

    val playbackState: StateFlow<PlaybackUiState> = playbackController.playbackState

    fun onTrackClick(index: Int) {
        playbackController.playTracks(uiState.value.tracks, index)
    }
}
