package com.lhacenmed.sona.core.data

import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.di.DefaultDispatcher
import com.lhacenmed.sona.core.common.sort.sortedByName
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.TrackEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The library's default sorting mode, mirrored from [LibrarySettings.intelligentSortingEnabled]. */
private const val DEFAULT_INTELLIGENT_SORTING = true

/**
 * The whole app's single view of the music library.
 *
 * Before this existed, nine different ViewModels each called `trackDao.observeAll()` and then
 * mapped, filtered and sorted the entire table themselves - inside `stateIn(viewModelScope, …)`,
 * whose transform runs on `Dispatchers.Main.immediate`. So every write to `tracks` fanned out into
 * up to nine full-library re-maps **on the main thread**, which is what made the list pop in and
 * stutter. Three properties fix that, and all three are load-bearing:
 *
 *  1. **Computed once.** Each list is derived exactly once here and shared; a tab, the player, the
 *     search screen and the theme all read the same already-sorted objects.
 *  2. **Computed off the main thread.** Everything is shared from an [ApplicationScope] running on
 *     the default dispatcher, so no mapping or sorting ever touches the frame.
 *  3. **Computed only when something changed.** [conflate] collapses a burst of writes mid-scan
 *     into one recompute, and [distinctUntilChanged] drops emissions whose *content* is identical -
 *     so a rescan that finds nothing new cannot repaint anything.
 *
 * Sharing is [SharingStarted.Eagerly] on a process-lifetime scope: the library survives a screen
 * being closed or the activity being recreated, so returning to it is a read from memory, not a
 * fresh round trip to SQLite.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val genreDao: GenreDao,
    librarySettings: LibrarySettings,
    @ApplicationScope private val scope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    /**
     * Seeded with the default rather than awaited, so the first paint is never blocked on a
     * DataStore disk read. If the stored value turns out to differ, exactly one re-sort follows.
     */
    private val intelligentSorting: StateFlow<Boolean> = librarySettings.intelligentSortingEnabled
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_INTELLIGENT_SORTING)

    val tracks: StateFlow<LibraryContent<Track>> = trackDao.observeAll()
        .shareSorted { entities, intelligent ->
            entities.map { it.toDomain() }.sortedByName(intelligent) { it.title }
        }

    val albums: StateFlow<LibraryContent<Album>> = albumDao.observeAll()
        .shareSorted { entities, intelligent ->
            entities.map { it.toDomain() }.sortedByName(intelligent) { it.title }
        }

    val artists: StateFlow<LibraryContent<Artist>> = artistDao.observeAll()
        .shareSorted { entities, intelligent ->
            entities.map { it.toDomain() }.sortedByName(intelligent) { it.name }
        }

    val genres: StateFlow<LibraryContent<Genre>> = genreDao.observeAll()
        .shareSorted { entities, intelligent ->
            entities.map { it.toDomain() }.sortedByName(intelligent) { it.name }
        }

    /** Aggregated by SQLite (`GROUP BY folderPath`), not by grouping the track list in memory. */
    val folders: StateFlow<LibraryContent<Folder>> = trackDao.observeFolders()
        .shareSorted { rows, intelligent ->
            rows.map { it.toDomain() }.sortedByName(intelligent) { it.name }
        }

    /**
     * Track lookup by id, for the player and the notification theme. Derived from [tracks] so it
     * costs one pass per library change rather than a fresh query - and crucially, so a playback
     * event (which fires several times a second) never re-reads the database at all.
     */
    val tracksById: StateFlow<Map<Long, Track>> = tracks
        .map { content -> content.itemsOrEmpty.associateBy { it.id } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** `true` once the library has been read from disk - the app's first-paint gate. */
    val isReady: StateFlow<Boolean> = tracks
        .map { !it.isLoading }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // region Scoped reads
    //
    // Detail screens used to observe the entire tracks table and filter it down to one album's
    // handful of rows - on the main thread, on every library change. These read only their own
    // rows, straight off the indices added for them.

    fun album(albumId: Long): Flow<Album?> =
        albumDao.observeById(albumId).map { it?.toDomain() }.distinctUntilChanged()

    fun artist(artistId: Long): Flow<Artist?> =
        artistDao.observeById(artistId).map { it?.toDomain() }.distinctUntilChanged()

    fun genre(genreId: Long): Flow<Genre?> =
        genreDao.observeById(genreId).map { it?.toDomain() }.distinctUntilChanged()

    /** Album tracks in playback order: disc, then track number, then title. */
    fun albumTracks(albumId: Long): Flow<LibraryContent<Track>> =
        trackDao.observeByAlbum(albumId).mapContent { entities ->
            entities.map { it.toDomain() }.sortedWith(
                compareBy(
                    { it.discNumber ?: Int.MAX_VALUE },
                    { it.trackNumber ?: Int.MAX_VALUE },
                    { it.title },
                ),
            )
        }

    fun artistTracks(artistId: Long): Flow<LibraryContent<Track>> =
        trackDao.observeByArtist(artistId).mapSortedContent { it.title }

    fun genreTracks(genreId: Long): Flow<LibraryContent<Track>> =
        trackDao.observeByGenre(genreId).mapSortedContent { it.title }

    fun folderTracks(folderPath: String): Flow<LibraryContent<Track>> =
        trackDao.observeByFolder(folderPath).mapSortedContent { it.title }

    /** Search runs as four `LIKE … LIMIT` queries rather than scanning the library in memory. */
    fun searchTracks(query: String, limit: Int): Flow<List<Track>> =
        trackDao.search(query, limit).map { entities -> entities.map { it.toDomain() } }

    fun searchAlbums(query: String, limit: Int): Flow<List<Album>> =
        albumDao.search(query, limit).map { entities -> entities.map { it.toDomain() } }

    fun searchArtists(query: String, limit: Int): Flow<List<Artist>> =
        artistDao.search(query, limit).map { entities -> entities.map { it.toDomain() } }

    fun searchGenres(query: String, limit: Int): Flow<List<Genre>> =
        genreDao.search(query, limit).map { entities -> entities.map { it.toDomain() } }

    /** Resolves persisted queue ids to tracks, in the order given. */
    suspend fun tracksByIds(ids: List<Long>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val byId = trackDao.getByIds(ids).associateBy({ it.id }, { it.toDomain() })
        return ids.mapNotNull { byId[it] }
    }

    suspend fun setFavorite(trackId: Long, isFavorite: Boolean) {
        trackDao.setFavorite(trackId, isFavorite)
    }

    // endregion

    private fun <E, T> Flow<List<E>>.shareSorted(
        transform: (List<E>, Boolean) -> List<T>,
    ): StateFlow<LibraryContent<T>> = conflate()
        // conflate() sits *upstream* of the transform on purpose. Room can fire several
        // invalidations in quick succession; without it each one would be mapped and sorted in
        // turn, and only the last result would ever be shown. With it, anything superseded while a
        // sort is still running is dropped instead of computed.
        .combine(intelligentSorting) { entities, intelligent ->
            LibraryContent.Ready(transform(entities, intelligent)) as LibraryContent<T>
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, LibraryContent.Loading)

    private fun <E, T> Flow<List<E>>.mapContent(
        transform: (List<E>) -> List<T>,
    ): Flow<LibraryContent<T>> =
        map { LibraryContent.Ready(transform(it)) as LibraryContent<T> }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)

    private fun Flow<List<TrackEntity>>.mapSortedContent(
        selector: (Track) -> String,
    ): Flow<LibraryContent<Track>> = combine(intelligentSorting) { entities, intelligent ->
        LibraryContent.Ready(
            entities.map { it.toDomain() }.sortedByName(intelligent, selector),
        ) as LibraryContent<Track>
    }.distinctUntilChanged().flowOn(defaultDispatcher)
}
