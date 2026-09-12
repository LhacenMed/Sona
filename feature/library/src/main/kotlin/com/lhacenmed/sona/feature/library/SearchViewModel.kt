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
import kotlinx.coroutines.flow.Flow
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

/**
 * How many results a filtered search returns.
 *
 * The per-section cap exists to stop any one kind of result from burying the others in the mixed
 * list. Once a filter is on there are no others to bury, and the user has said which kind they want
 * - so the cap stops being a courtesy to the layout and starts being a missing result.
 */
private const val MAX_FILTERED_RESULTS = 100

/**
 * A kind of result the search can be narrowed to.
 *
 * Declared here rather than in the screen because it decides which queries run, not merely which
 * results are drawn: narrowing to albums means the other three queries are never issued.
 */
enum class SearchFilter(val label: String) {
    Tracks("Tracks"),
    Albums("Albums"),
    Artists("Artists"),
    Genres("Genres"),
}

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

    private val _filter = MutableStateFlow<SearchFilter?>(null)

    /** Which kind of result the search is narrowed to, or null for all of them. */
    val filter: StateFlow<SearchFilter?> = _filter.asStateFlow()

    val currentTrackId: StateFlow<Long?> = playbackController.playbackState
        .map { it.currentTrackId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Whether that track is actually playing, which is what the playing indicator animates on.
     * Split from [currentTrackId] for the same reason it exists: the two change at different
     * moments, and a row that took both as one value would recompose on each.
     */
    val isPlaying: StateFlow<Boolean> = playbackController.playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Search is a handful of `LIKE … LIMIT` queries - four, or one if a filter is on - re-issued
     * whenever the query or the filter changes.
     *
     * It used to hold the entire library in memory - four full tables, mapped to domain objects -
     * and filter all of it on the main thread on every keystroke, which meant opening the search
     * screen alone duplicated the whole library. Letting SQLite do the matching means the work is
     * proportional to the *results*, not to the library, and it is a screen you can open on a
     * 50,000-track device without a pause.
     */
    val uiState: StateFlow<SearchUiState> = combine(
        // Debounced, because this side of the pair changes once per keystroke. The filter is not:
        // it changes once per tap, and a tap that waits 200ms to do anything feels broken.
        _query.debounce(200).map { it.trim() }.distinctUntilChanged(),
        _filter,
    ) { query, filter -> query to filter }
        .flatMapLatest { (query, filter) ->
            // A blank query shows nothing rather than the whole library.
            if (query.isBlank()) flowOf(SearchUiState()) else search(query, filter)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /**
     * The queries a search actually runs.
     *
     * A filter is applied here rather than to the finished results so that narrowing the search also
     * narrows the work: one query instead of four, and a deeper one, since the section cap only
     * earns its keep while there are other sections to protect.
     */
    private fun search(query: String, filter: SearchFilter?): Flow<SearchUiState> = when (filter) {
        null -> combine(
            repository.searchTracks(query, MAX_RESULTS_PER_SECTION),
            repository.searchAlbums(query, MAX_RESULTS_PER_SECTION),
            repository.searchArtists(query, MAX_RESULTS_PER_SECTION),
            repository.searchGenres(query, MAX_RESULTS_PER_SECTION),
        ) { tracks, albums, artists, genres ->
            SearchUiState(tracks, albums, artists, genres)
        }

        SearchFilter.Tracks -> repository.searchTracks(query, MAX_FILTERED_RESULTS)
            .map { SearchUiState(tracks = it) }

        SearchFilter.Albums -> repository.searchAlbums(query, MAX_FILTERED_RESULTS)
            .map { SearchUiState(albums = it) }

        SearchFilter.Artists -> repository.searchArtists(query, MAX_FILTERED_RESULTS)
            .map { SearchUiState(artists = it) }

        SearchFilter.Genres -> repository.searchGenres(query, MAX_FILTERED_RESULTS)
            .map { SearchUiState(genres = it) }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /**
     * Tapping a filter that is already on clears it. Without that there would be no way back to the
     * mixed results once a filter has been picked, since the row has no chip standing for "all".
     */
    fun onFilterClick(filter: SearchFilter) {
        _filter.value = if (_filter.value == filter) null else filter
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
