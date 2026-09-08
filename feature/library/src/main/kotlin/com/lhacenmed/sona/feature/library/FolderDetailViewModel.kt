package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class FolderDetailUiState(
    val folderName: String = "",
    val tracks: List<Track> = emptyList(),
)

@HiltViewModel(assistedFactory = FolderDetailViewModel.Factory::class)
class FolderDetailViewModel @AssistedInject constructor(
    @Assisted private val folderPath: String,
    trackDao: TrackDao,
    private val playbackController: PlaybackController,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(folderPath: String): FolderDetailViewModel
    }

    private val folderName = folderPath.substringAfterLast('/')

    val uiState: StateFlow<FolderDetailUiState> = trackDao.observeAll()
        .map { entities ->
            val tracks = entities.map { it.toDomain() }
                .filter { it.folderPath == folderPath }
                .sortedBy { it.title }
            FolderDetailUiState(folderName = folderName, tracks = tracks)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            FolderDetailUiState(folderName = folderName),
        )

    val playbackState: StateFlow<PlaybackUiState> = playbackController.playbackState

    fun onTrackClick(index: Int) {
        playbackController.playTracks(uiState.value.tracks, index)
    }
}
