package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val MAX_RESULTS_PER_SECTION = 10

data class SearchUiState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && genres.isEmpty()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val currentTrackId: StateFlow<Long?> = playbackController.playbackState
        .map { it.currentTrackId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Search is four `LIKE … LIMIT 10` queries, re-issued per keystroke (after a debounce).
     *
     * It used to hold the entire library in memory - four full tables, mapped to domain objects -
     * and filter all of it on the main thread on every keystroke, which meant opening the search
     * screen alone duplicated the whole library. Letting SQLite do the matching means the work is
     * proportional to the *results*, not to the library, and it is a screen you can open on a
     * 50,000-track device without a pause.
     */
    val uiState: StateFlow<SearchUiState> = _query
        .debounce(200)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            // A blank query shows nothing rather than the whole library.
            if (query.isBlank()) {
                flowOf(SearchUiState())
            } else {
                combine(
                    repository.searchTracks(query, MAX_RESULTS_PER_SECTION),
                    repository.searchAlbums(query, MAX_RESULTS_PER_SECTION),
                    repository.searchArtists(query, MAX_RESULTS_PER_SECTION),
                    repository.searchGenres(query, MAX_RESULTS_PER_SECTION),
                ) { tracks, albums, artists, genres ->
                    SearchUiState(tracks, albums, artists, genres)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onTrackClick(track: Track) {
        val matching = uiState.value.tracks
        val index = matching.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playTracks(matching, index)
    }

    /**
     * Plays the selected results. Only track results can be selected - an album and an artist have
     * no single action in common, so letting them into a selection would give the bar nothing
     * honest to offer.
     */
    fun playSelection(selectedKeys: Set<Any>) {
        val selected = uiState.value.tracks.filter { it.id in selectedKeys }
        if (selected.isNotEmpty()) playbackController.playTracks(selected, startIndex = 0)
    }

    /** Every track result's selection key, which is what the context bar's "select all" selects. */
    fun selectableKeys(): List<Any> = uiState.value.tracks.map { it.id }
}
