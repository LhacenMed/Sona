package com.lhacenmed.sona.feature.library

import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.model.Genre
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = GenreDetailViewModel.Factory::class)
class GenreDetailViewModel @AssistedInject constructor(
    @Assisted genreId: Long,
    repository: LibraryRepository,
    sortOrders: LibrarySortOrders,
    playbackController: PlaybackController,
) : TrackListDetailViewModel(playbackController, repository) {

    @AssistedFactory
    interface Factory {
        fun create(genreId: Long): GenreDetailViewModel
    }

    override val playbackParent: PlaybackParent = PlaybackParent.Genre(genreId)

    val genre: StateFlow<Genre?> = repository.genre(genreId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override val tracks: StateFlow<LibraryContent<Track>> = repository.genreTracks(genreId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent.Loading)

    override val sort: SortControl = sortOrders.control(SortableList.GENRE_TRACKS, genreId.toString())

    /** The artists with tracks in the genre, by name - Auxio's `GENRE_ARTIST_SORT`. */
    override val sections: StateFlow<List<DetailSection>> = combine(repository.artists, tracks) { artists, tracks ->
        val artistIds = tracks.itemsOrEmpty.mapTo(HashSet()) { it.artistId }
        val genreArtists = artists.itemsOrEmpty
            .filter { it.id in artistIds }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        listOfNotNull(genreArtists.takeIf { it.isNotEmpty() }?.let(DetailSection::Artists))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
