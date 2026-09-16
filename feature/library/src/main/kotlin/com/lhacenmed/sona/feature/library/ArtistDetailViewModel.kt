package com.lhacenmed.sona.feature.library

import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.sort.SortableList
import com.lhacenmed.sona.feature.library.sort.SortControl
import com.lhacenmed.sona.feature.library.sort.control
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = ArtistDetailViewModel.Factory::class)
class ArtistDetailViewModel @AssistedInject constructor(
    @Assisted artistId: Long,
    repository: LibraryRepository,
    sortOrders: LibrarySortOrders,
    playbackController: PlaybackController,
) : TrackListDetailViewModel(playbackController, repository) {

    @AssistedFactory
    interface Factory {
        fun create(artistId: Long): ArtistDetailViewModel
    }

    override val playbackParent: PlaybackParent = PlaybackParent.Artist(artistId)

    val artist: StateFlow<Artist?> = repository.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override val tracks: StateFlow<LibraryContent<Track>> = repository.artistTracks(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent.Loading)

    override val sort: SortControl = sortOrders.control(SortableList.ARTIST_TRACKS)
}
