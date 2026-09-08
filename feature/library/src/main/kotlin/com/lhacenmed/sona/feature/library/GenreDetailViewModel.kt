package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.model.Genre
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

data class GenreDetailUiState(
    val genre: Genre? = null,
    val tracks: List<Track> = emptyList(),
)

@HiltViewModel(assistedFactory = GenreDetailViewModel.Factory::class)
class GenreDetailViewModel @AssistedInject constructor(
    @Assisted private val genreId: Long,
    trackDao: TrackDao,
    genreDao: GenreDao,
    private val playbackController: PlaybackController,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(genreId: Long): GenreDetailViewModel
    }

    val uiState: StateFlow<GenreDetailUiState> = combine(
        genreDao.observeAll()
            .map { entities -> entities.map { it.toDomain() }.find { it.id == genreId } },
        trackDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
                .filter { it.genreId == genreId }
                .sortedBy { it.title }
        },
    ) { genre, tracks -> GenreDetailUiState(genre = genre, tracks = tracks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GenreDetailUiState())

    val playbackState: StateFlow<PlaybackUiState> = playbackController.playbackState

    fun onTrackClick(index: Int) {
        playbackController.playTracks(uiState.value.tracks, index)
    }
}
