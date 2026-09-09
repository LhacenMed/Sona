package com.lhacenmed.sona.feature.library

import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = FolderDetailViewModel.Factory::class)
class FolderDetailViewModel @AssistedInject constructor(
    @Assisted private val folderPath: String,
    repository: LibraryRepository,
    playbackController: PlaybackController,
) : TrackListDetailViewModel(playbackController) {

    @AssistedFactory
    interface Factory {
        fun create(folderPath: String): FolderDetailViewModel
    }

    val folderName: String = folderPath.substringAfterLast('/')

    override val tracks: StateFlow<LibraryContent<Track>> = repository.folderTracks(folderPath)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent.Loading)
}
