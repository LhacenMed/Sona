package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.model.Artist
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

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val tracks: List<Track> = emptyList(),
)

@HiltViewModel(assistedFactory = ArtistDetailViewModel.Factory::class)
class ArtistDetailViewModel @AssistedInject constructor(
    @Assisted private val artistId: Long,
    trackDao: TrackDao,
    artistDao: ArtistDao,
    private val playbackController: PlaybackController,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(artistId: Long): ArtistDetailViewModel
    }

    val uiState: StateFlow<ArtistDetailUiState> = combine(
        artistDao.observeAll()
            .map { entities -> entities.map { it.toDomain() }.find { it.id == artistId } },
        trackDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
                .filter { it.artistId == artistId }
                .sortedBy { it.title }
        },
    ) { artist, tracks -> ArtistDetailUiState(artist = artist, tracks = tracks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistDetailUiState())

    val playbackState: StateFlow<PlaybackUiState> = playbackController.playbackState

    fun onTrackClick(index: Int) {
        playbackController.playTracks(uiState.value.tracks, index)
    }
}
