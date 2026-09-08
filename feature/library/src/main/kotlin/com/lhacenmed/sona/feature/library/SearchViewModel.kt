package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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

@HiltViewModel
class SearchViewModel @Inject constructor(
    trackDao: TrackDao,
    albumDao: AlbumDao,
    artistDao: ArtistDao,
    genreDao: GenreDao,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val playbackState: StateFlow<PlaybackUiState> = playbackController.playbackState

    private val tracks = trackDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    private val albums = albumDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    private val artists = artistDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    private val genres = genreDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    val uiState: StateFlow<SearchUiState> = combine(
        _query.debounce(200),
        tracks,
        albums,
        artists,
        genres,
    ) { query, allTracks, allAlbums, allArtists, allGenres ->
        // A blank query shows nothing rather than the whole library.
        if (query.isBlank()) {
            SearchUiState()
        } else {
            SearchUiState(
                tracks = allTracks.filter { it.title.contains(query, ignoreCase = true) }
                    .take(MAX_RESULTS_PER_SECTION),
                albums = allAlbums.filter { it.title.contains(query, ignoreCase = true) }
                    .take(MAX_RESULTS_PER_SECTION),
                artists = allArtists.filter { it.name.contains(query, ignoreCase = true) }
                    .take(MAX_RESULTS_PER_SECTION),
                genres = allGenres.filter { it.name.contains(query, ignoreCase = true) }
                    .take(MAX_RESULTS_PER_SECTION),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onTrackClick(track: Track) {
        val matching = uiState.value.tracks
        val index = matching.indexOf(track)
        if (index >= 0) playbackController.playTracks(matching, index)
    }
}
