package com.lhacenmed.sona.core.data

import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.di.DefaultDispatcher
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.data.sort.LibrarySortSpecs
import com.lhacenmed.sona.core.data.sort.PlaylistEntry
import com.lhacenmed.sona.core.data.sort.SortSpec
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.FAVORITES_PLAYLIST_ID
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.PlayStatsDao
import com.lhacenmed.sona.core.database.dao.PlaylistDao
import com.lhacenmed.sona.core.database.entity.PlaylistEntity
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.sort.SortTarget
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
 * Every list is sorted the way the user last chose for it (see [LibrarySortOrders]), and re-sorted
 * here - not on screen - the moment that choice changes.
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
    private val playlistDao: PlaylistDao,
    private val playStatsDao: PlayStatsDao,
    private val sortOrders: LibrarySortOrders,
    librarySettings: LibrarySettings,
    @ApplicationScope private val scope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    /**
     * Starts from the stored mode, which is already in memory, so the first sort is the one the user
     * chose - the library is never shown sorted one way and then re-sorted the other.
     */
    private val intelligentSorting: StateFlow<Boolean> = librarySettings.intelligentSortingEnabled.flow
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, librarySettings.intelligentSortingEnabled.value)

    val tracks: StateFlow<LibraryContent<Track>> = trackDao.observeAll()
        .sortedFor(LibrarySortSpecs.tracks) { rows -> rows.map { it.toDomain() } }
        .shareContent()

    val albums: StateFlow<LibraryContent<Album>> = albumDao.observeAll()
        .sortedFor(LibrarySortSpecs.albums) { rows -> rows.map { it.toDomain() } }
        .shareContent()

    val artists: StateFlow<LibraryContent<Artist>> = artistDao.observeAll()
        .sortedFor(LibrarySortSpecs.artists) { rows -> rows.map { it.toDomain() } }
        .shareContent()

    val genres: StateFlow<LibraryContent<Genre>> = genreDao.observeAll()
        .sortedFor(LibrarySortSpecs.genres) { rows -> rows.map { it.toDomain() } }
        .shareContent()

    /** Aggregated by SQLite (`GROUP BY folderPath`), not by grouping the track list in memory. */
    val folders: StateFlow<LibraryContent<Folder>> = trackDao.observeFolders()
        .combine(trackDao.observeFolderCoverArt()) { rows, coverRows ->
            val coversByPath = coverRows.groupBy { it.path }
            rows.map { row ->
                row.toDomain(
                    coverArtUris = rankedCoverArtUris(
                        coversByPath[row.path].orEmpty().associate { it.coverArtUri to it.trackCount },
                    ),
                )
            }
        }
        .sortedFor(LibrarySortSpecs.folders) { rows -> rows }
        .shareContent()

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

    /** Album tracks, in playback order - disc, then track number - unless sorted otherwise. */
    fun albumTracks(albumId: Long): Flow<LibraryContent<Track>> =
        trackDao.observeByAlbum(albumId)
            .sortedFor(LibrarySortSpecs.albumTracks, albumId.toString()) { rows -> rows.map { it.toDomain() } }
            .asContent()

    fun artistTracks(artistId: Long): Flow<LibraryContent<Track>> =
        trackDao.observeByArtist(artistId)
            .sortedFor(LibrarySortSpecs.artistTracks, artistId.toString()) { rows -> rows.map { it.toDomain() } }
            .asContent()

    fun genreTracks(genreId: Long): Flow<LibraryContent<Track>> =
        trackDao.observeByGenre(genreId)
            .sortedFor(LibrarySortSpecs.genreTracks, genreId.toString()) { rows -> rows.map { it.toDomain() } }
            .asContent()

    fun folderTracks(folderPath: String): Flow<LibraryContent<Track>> =
        trackDao.observeByFolder(folderPath)
            .sortedFor(LibrarySortSpecs.folderTracks, folderPath) { rows -> rows.map { it.toDomain() } }
            .asContent()

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

    /** Every playlist in the chosen order, Favorites first, with the count and covers each row shows. */
    val playlists: StateFlow<LibraryContent<Playlist>> = playlistDao.observeAll()
        .sortedFor(LibrarySortSpecs.playlists) { rows -> rows }
        .combine(playlistDao.observeCoverArt()) { rows, coverRows ->
            val coversByPlaylist = coverRows.groupBy { it.playlistId }
            rows
                // Stable, so the chosen order holds among the rest. Favorites is the one playlist
                // every user has, and it keeps the top whatever playlists are sorted by.
                .sortedByDescending { it.isBuiltIn }
                .map { row ->
                    Playlist(
                        id = row.id,
                        name = row.name,
                        isBuiltIn = row.isBuiltIn,
                        trackCount = row.trackCount,
                        coverArtUris = rankedCoverArtUris(
                            coversByPlaylist[row.id].orEmpty().associate { it.coverArtUri to it.trackCount },
                        ),
                    )
                }
        }
        .shareContent()

    /** How many tracks each derived list would show, for the playlists tab's subtitles. */
    val recentlyPlayedCount: StateFlow<Int> = playStatsDao.observeRecentlyPlayedCount()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    val mostPlayedCount: StateFlow<Int> = playStatsDao.observeMostPlayedCount()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /**
     * A playlist's tracks, in the order they are sorted - the order the user arranged, by default.
     *
     * Sorting only changes what is shown. The arranged order is never rewritten by it: it stays the
     * Custom order, which is the one dragging edits.
     */
    fun playlistTracks(playlistId: Long): Flow<LibraryContent<Track>> =
        playlistDao.observeTracks(playlistId)
            .sortedFor(LibrarySortSpecs.playlistTracks, playlistId.toString()) { rows ->
                rows.mapIndexed { position, row ->
                    PlaylistEntry(track = row.track.toDomain(), position = position, addedAt = row.addedAt)
                }
            }
            .map { entries -> entries.map { it.track } }
            .asContent()

    /**
     * Creates a playlist and returns its id, or null when the name is already taken.
     *
     * The uniqueness rule is the database's own (a unique index on `name`), so two screens racing
     * to create the same name cannot both win - the loser simply gets null back.
     */
    /** Favorites is an ordinary playlist, so screens open it the same way as any other. */
    val favoritesPlaylistId: Long get() = FAVORITES_PLAYLIST_ID

    suspend fun createPlaylist(name: String): Long? {
        val createdAt = System.currentTimeMillis()
        return runCatching {
            playlistDao.insert(
                PlaylistEntity(name = name.trim(), createdAt = createdAt, modifiedAt = createdAt),
            )
        }.getOrNull()
    }

    /** Renames a playlist. Built-in ones are refused by the query itself, not by the caller. */
    suspend fun renamePlaylist(playlistId: Long, name: String) {
        playlistDao.rename(playlistId, name.trim(), System.currentTimeMillis())
    }

    /** Deletes a playlist and its membership. The tracks themselves are untouched. */
    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.delete(playlistId)
    }

    suspend fun removeTracksFromPlaylist(playlistId: Long, trackIds: List<Long>) {
        playlistDao.removeTracks(playlistId, trackIds, System.currentTimeMillis())
    }

    /** Persists the order a drag ended on, in one transaction. */
    suspend fun setPlaylistOrder(playlistId: Long, trackIds: List<Long>) {
        playlistDao.setOrder(playlistId, trackIds, System.currentTimeMillis())
    }

    /** Appends tracks to a playlist, keeping the position of any already in it. */
    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
        playlistDao.addTracks(playlistId, trackIds, System.currentTimeMillis())
    }

    /** The Favorites playlist's tracks. Its id lives here so no screen has to know it. */
    fun favoriteTracks(): Flow<LibraryContent<Track>> = playlistTracks(FAVORITES_PLAYLIST_ID)

    /** "Recent" and "Most played" - ordered by the statistics, so likewise never re-sorted. */
    fun recentlyPlayedTracks(): Flow<LibraryContent<Track>> =
        playStatsDao.observeRecentlyPlayed().map { entities ->
            LibraryContent.Ready(entities.map { it.toDomain() })
        }

    fun mostPlayedTracks(): Flow<LibraryContent<Track>> =
        playStatsDao.observeMostPlayed().map { entities ->
            LibraryContent.Ready(entities.map { it.toDomain() })
        }

    /**
     * The ids in Favorites, for anything that only needs to know whether a track is one.
     *
     * A set rather than a list of tracks: the player asks this about a single track on every song
     * change, and the playlist screen already reads the tracks themselves in order.
     */
    val favoriteTrackIds: StateFlow<Set<Long>> = playlistDao.observeTrackIds(FAVORITES_PLAYLIST_ID)
        .map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    suspend fun setFavorite(trackId: Long, isFavorite: Boolean) {
        val changedAt = System.currentTimeMillis()
        if (isFavorite) {
            playlistDao.addTracks(FAVORITES_PLAYLIST_ID, listOf(trackId), changedAt)
        } else {
            playlistDao.removeTracks(FAVORITES_PLAYLIST_ID, listOf(trackId), changedAt)
        }
    }

    // endregion

    /**
     * Turns rows into [spec]'s items, sorted the way its list is set to, and sorts them again
     * whenever that order or the name-sorting mode changes.
     */
    private fun <R, T> Flow<List<R>>.sortedFor(
        spec: SortSpec<T>,
        // Which list of its kind this is, for the lists there can be many of - so one playlist's order
        // is its own. The library's own lists are the only one of their kind and name nothing here.
        instanceId: String? = null,
        toItems: (List<R>) -> List<T>,
    ): Flow<List<T>> =
        // conflate() sits *upstream* of the transform on purpose. Room can fire several
        // invalidations in quick succession; without it each one would be mapped and sorted in
        // turn, and only the last result would ever be shown. With it, anything superseded while a
        // sort is still running is dropped instead of computed.
        combine(
            conflate(),
            sortOrders.order(SortTarget(spec.list, instanceId)),
            intelligentSorting,
        ) { rows, order, intelligent ->
            spec.sort(toItems(rows), order, intelligent)
        }

    private fun <T> Flow<List<T>>.shareContent(): StateFlow<LibraryContent<T>> =
        map { LibraryContent.Ready(it) as LibraryContent<T> }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, LibraryContent.Loading)

    private fun <T> Flow<List<T>>.asContent(): Flow<LibraryContent<T>> =
        map { LibraryContent.Ready(it) as LibraryContent<T> }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
}
