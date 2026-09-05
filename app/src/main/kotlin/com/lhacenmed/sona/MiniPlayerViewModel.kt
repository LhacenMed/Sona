package com.lhacenmed.sona

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MiniPlayerUiState(
    val playback: PlaybackUiState = PlaybackUiState(),
    val currentTrack: Track? = null,
)

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    trackDao: TrackDao,
    private val playbackController: PlaybackController,
) : ViewModel() {

    val uiState: StateFlow<MiniPlayerUiState> = combine(
        playbackController.playbackState,
        trackDao.observeAll().map { entities -> entities.map { it.toDomain() } },
    ) { playback, tracks ->
        MiniPlayerUiState(
            playback = playback,
            currentTrack = tracks.find { it.id == playback.currentTrackId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiniPlayerUiState())

    fun onTogglePlayPause() {
        playbackController.togglePlayPause()
    }
}
