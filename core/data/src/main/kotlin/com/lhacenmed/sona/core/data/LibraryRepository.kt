package com.lhacenmed.sona.core.data

import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.di.DefaultDispatcher
import com.lhacenmed.sona.core.data.playlist.PlaylistCoverImages
import com.lhacenmed.sona.core.data.playlist.PlaylistFile
import com.lhacenmed.sona.core.data.playlist.cover
import com.lhacenmed.sona.core.data.playlist.coverArtUris
import com.lhacenmed.sona.core.data.playlist.source
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.data.sort.LibrarySortSpecs
import com.lhacenmed.sona.core.data.sort.ListEntry
import com.lhacenmed.sona.core.data.sort.SortSpec
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArrangementDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.FavoriteCollectionDao
import com.lhacenmed.sona.core.database.FAVORITES_PLAYLIST_ID
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.PlayStatsDao
import com.lhacenmed.sona.core.database.dao.PlaylistDao
import com.lhacenmed.sona.core.database.entity.FavoriteCollectionEntity
import com.lhacenmed.sona.core.database.entity.PlaylistEntity
import com.lhacenmed.sona.core.database.entity.PlaylistTrackEntity
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.PlaylistCover
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.playbackParentOf
import com.lhacenmed.sona.core.model.toStorageKey
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortTarget
import com.lhacenmed.sona.core.model.sort.SortableList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
    private val arrangementDao: ArrangementDao,
    private val favoriteCollectionDao: FavoriteCollectionDao,
    private val playStatsDao: PlayStatsDao,
    private val playlistCoverImages: PlaylistCoverImages,
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

    /** The music - every track but the videos, which are the Videos tab's alone. */
    val tracks: StateFlow<LibraryContent<Track>> = trackDao.observeAll(isVideo = false)
        .sortedFor(LibrarySortSpecs.tracks) { rows -> rows.map { it.toDomain() } }
        .shareContent()

    /** Every video - the Videos tab. */
    val videos: StateFlow<LibraryContent<Track>> = trackDao.observeAll(isVideo = true)
        .sortedFor(LibrarySortSpecs.videos) { rows -> rows.map { it.toDomain() } }
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

    val folders: StateFlow<LibraryContent<Folder>> = foldersOf(isVideo = false)

    /** Every folder holding a video, counting its videos alone - sorted as [folders] are. */
    val videoFolders: StateFlow<LibraryContent<Folder>> = foldersOf(isVideo = true)

    /**
     * Track lookup by id - the videos' too, as they are queued like any track - for the player and the
     * notification theme. Derived from [tracks] so it costs one pass per library change rather than a
     * fresh query - and crucially, so a playback event (which fires several times a second) never
     * re-reads the database at all.
     */
    val tracksById: StateFlow<Map<Long, Track>> = tracks
        .combine(videos) { tracks, videos -> (tracks.itemsOrEmpty + videos.itemsOrEmpty).associateBy { it.id } }
        .flowOn(defaultDispatcher)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * The section each row of a library tab sits in under that tab's current sort - what a fast
     * scroller's popup names it by. Kept in step with the sort the list itself is in.
     */
    val trackSections: StateFlow<(Track) -> String?> = sectionsOf(LibrarySortSpecs.tracks)
    val videoSections: StateFlow<(Track) -> String?> = sectionsOf(LibrarySortSpecs.videos)
    val albumSections: StateFlow<(Album) -> String?> = sectionsOf(LibrarySortSpecs.albums)
    val artistSections: StateFlow<(Artist) -> String?> = sectionsOf(LibrarySortSpecs.artists)
    val genreSections: StateFlow<(Genre) -> String?> = sectionsOf(LibrarySortSpecs.genres)
    val folderSections: StateFlow<(Folder) -> String?> = sectionsOf(LibrarySortSpecs.folders)

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
            .arrangedFor(LibrarySortSpecs.albumTracks, albumId.toString()) { ListEntry(it.toDomain()) }
            .asTrackContent()

    fun artistTracks(artistId: Long): Flow<LibraryContent<Track>> =
        trackDao.observeByArtist(artistId)
            .arrangedFor(LibrarySortSpecs.artistTracks, artistId.toString()) { ListEntry(it.toDomain()) }
            .asTrackContent()

    fun genreTracks(genreId: Long): Flow<LibraryContent<Track>> =
        trackDao.observeByGenre(genreId)
            .arrangedFor(LibrarySortSpecs.genreTracks, genreId.toString()) { ListEntry(it.toDomain()) }
            .asTrackContent()

    /** The music in [folderPath] - or, where [isVideo], its videos. */
    fun folderTracks(folderPath: String, isVideo: Boolean): Flow<LibraryContent<Track>> =
        trackDao.observeByFolder(folderPath, isVideo)
            .arrangedFor(LibrarySortSpecs.folderTracks, folderPath) { ListEntry(it.toDomain()) }
            .asTrackContent()

    /** Resolves persisted queue ids to tracks, in the order given. */
    suspend fun tracksByIds(ids: List<Long>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val byId = trackDao.getByIds(ids).associateBy({ it.id }, { it.toDomain() })
        return ids.mapNotNull { byId[it] }
    }

    /**
     * The tracks, in their current sort, of every playlist whose cover is its first or last track - of
     * those alone, so a playlist with any other cover never has its tracks read to draw it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val tracksOfPlaylistsCoveredBySortedTrack: Flow<Map<Long, List<Track>>> =
        playlistDao.observeIdsCoveredBySortedTrack()
            .distinctUntilChanged()
            .flatMapLatest { playlistIds ->
                if (playlistIds.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(playlistIds.map { id -> playlistTracks(id).map { id to it.itemsOrEmpty } }) { it.toMap() }
                }
            }

    /** Every playlist in the chosen order, Favorites first, with the count and cover each row shows. */
    val playlists: StateFlow<LibraryContent<Playlist>> = combine(
        playlistDao.observeAll().sortedFor(LibrarySortSpecs.playlists) { rows -> rows },
        playlistDao.observeCoverArt(),
        tracksOfPlaylistsCoveredBySortedTrack,
    ) { rows, coverRows, sortedTracksByPlaylist ->
        val coversByPlaylist = coverRows.groupBy { it.playlistId }
        rows
            // Stable, so the chosen order holds among the rest. Favorites is the one playlist
            // every user has, and it keeps the top whatever playlists are sorted by.
            .sortedByDescending { it.isBuiltIn }
            .map { row ->
                val cover = row.cover()
                Playlist(
                    id = row.id,
                    name = row.name,
                    isBuiltIn = row.isBuiltIn,
                    trackCount = row.trackCount,
                    cover = cover,
                    coverArtUris = cover.coverArtUris(
                        stackedCoverArtUris = rankedCoverArtUris(
                            coversByPlaylist[row.id].orEmpty().associate { it.coverArtUri to it.trackCount },
                        ),
                        sortedTracks = sortedTracksByPlaylist[row.id].orEmpty(),
                        chosenTrackCoverArtUri = row.coverTrackArtUri,
                    ),
                )
            }
    }
        .shareContent()

    /** How many tracks Most played would show - its row's subtitle, and whether it is listed at all. */
    val mostPlayedCount: StateFlow<Int> = playStatsDao.observeMostPlayedCount()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /**
     * A playlist's tracks, in the order they are sorted - the ones added last first, by default.
     *
     * Sorting only changes what is shown. The arranged order is never rewritten by it: it stays the
     * Custom order, which is the one dragging edits.
     */
    fun playlistTracks(playlistId: Long): Flow<LibraryContent<Track>> = playlistEntries(playlistId).asTrackContent()

    private fun playlistEntries(playlistId: Long): Flow<List<ListEntry>> =
        playlistDao.observeTracks(playlistId)
            .arrangedFor(LibrarySortSpecs.playlistTracks, playlistId.toString()) { row ->
                ListEntry(track = row.track.toDomain(), addedAt = row.addedAt)
            }

    /** A playlist as its file carries it: as it is shown, when each track joined it, and how it is sorted. */
    suspend fun playlistFile(playlistId: Long): PlaylistFile = PlaylistFile(
        entries = playlistEntries(playlistId).first().map { PlaylistFile.Entry(it.track, it.addedAt) },
        order = sortOrders.currentOrder(playlistTarget(playlistId)),
    )

    /**
     * Creates a playlist named [name] holding [file]'s tracks, as it was exported: each track's date
     * added, the order it was shown in as its arrangement, and its sort. Whether it was created - not
     * when the file names no track this library has, or the name is taken, leaving nothing behind.
     */
    suspend fun importPlaylist(name: String, file: PlaylistFile): Boolean {
        if (file.entries.isEmpty()) return false
        val playlistId = createPlaylist(name) ?: return false
        val importedAt = System.currentTimeMillis()
        playlistDao.addTracks(
            playlistId,
            file.entries.mapIndexed { index, entry ->
                PlaylistTrackEntity(playlistId, entry.track.id, entry.addedAt ?: (importedAt + index))
            },
            importedAt,
        )
        sortOrders.restore(playlistTarget(playlistId), file.entries.map { it.track.id }, file.order)
        return true
    }

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

    /**
     * Renames a playlist and sets its cover, as one change - or throws, leaving it as it was, when the
     * name is taken or a picked image cannot be read. A built-in playlist keeps its name and takes the cover.
     *
     * A picked image is copied in only here, on saving, so an edit given up on leaves no copy behind;
     * the copy it replaces is deleted once the change is stored.
     */
    suspend fun editPlaylist(playlistId: Long, name: String, cover: PlaylistCover) {
        val previousImageUri = playlistDao.coverImageUri(playlistId)
        val storedCover = if (cover is PlaylistCover.Image && !playlistCoverImages.isOwned(cover.uri)) {
            PlaylistCover.Image(playlistCoverImages.import(cover.uri))
        } else {
            cover
        }
        val imageUri = (storedCover as? PlaylistCover.Image)?.uri
        try {
            playlistDao.edit(
                playlistId = playlistId,
                name = name.trim(),
                coverSource = storedCover.source,
                coverTrackId = (storedCover as? PlaylistCover.OfTrack)?.trackId,
                coverImageUri = imageUri,
                editedAt = System.currentTimeMillis(),
            )
        } catch (failure: Exception) {
            if (imageUri != null && imageUri != previousImageUri) playlistCoverImages.delete(imageUri)
            throw failure
        }
        if (previousImageUri != null && previousImageUri != imageUri) playlistCoverImages.delete(previousImageUri)
    }

    /** Deletes a playlist, its membership, its order and its cover image. The tracks themselves are untouched. */
    suspend fun deletePlaylist(playlistId: Long) {
        val imageUri = playlistDao.coverImageUri(playlistId)
        if (!playlistDao.delete(playlistId)) return
        sortOrders.forget(playlistTarget(playlistId))
        if (imageUri != null) playlistCoverImages.delete(imageUri)
    }

    suspend fun removeTracksFromPlaylist(playlistId: Long, trackIds: List<Long>) {
        playlistDao.removeTracks(playlistId, trackIds, System.currentTimeMillis())
    }

    /** Adds tracks to a playlist as new ones, keeping the place and date of any already in it. */
    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
        val addedAt = System.currentTimeMillis()
        playlistDao.addTracks(playlistId, membershipsOf(playlistId, trackIds, addedAt), addedAt)
    }

    /** "Recent" and "Most played" - ordered by the statistics, so likewise never re-sorted. */
    fun recentlyPlayedTracks(): Flow<LibraryContent<Track>> =
        playStatsDao.observeRecentlyPlayed().map { entities ->
            LibraryContent.Ready(entities.map { it.toDomain() })
        }

    fun mostPlayedTracks(): Flow<LibraryContent<Track>> =
        playStatsDao.observeMostPlayed().map { entities ->
            LibraryContent.Ready(entities.map { it.toDomain() })
        }

    /** [parent]'s tracks, in the order its own list shows them - what playing it from anywhere plays. */
    fun collectionTracks(parent: PlaybackParent): Flow<LibraryContent<Track>> = when (parent) {
        is PlaybackParent.Album -> albumTracks(parent.albumId)
        is PlaybackParent.Artist -> artistTracks(parent.artistId)
        is PlaybackParent.Genre -> genreTracks(parent.genreId)
        is PlaybackParent.Playlist -> playlistTracks(parent.playlistId)
        is PlaybackParent.Folder -> folderTracks(parent.folderPath, parent.isVideo)
        PlaybackParent.RecentlyPlayed -> recentlyPlayedTracks()
        PlaybackParent.MostPlayed -> mostPlayedTracks()
        PlaybackParent.Videos -> videos
    }

    /**
     * [parent]'s name as the library has it now, or null once it is no longer in the library - and for
     * the videos and the two listening histories, which have no name of their own to give.
     */
    fun collectionName(parent: PlaybackParent): Flow<String?> = when (parent) {
        is PlaybackParent.Album -> album(parent.albumId).map { it?.title }
        is PlaybackParent.Artist -> artist(parent.artistId).map { it?.name }
        is PlaybackParent.Genre -> genre(parent.genreId).map { it?.name }
        is PlaybackParent.Playlist -> playlists.readyItems().map { all -> all.find { it.id == parent.playlistId }?.name }
        is PlaybackParent.Folder -> (if (parent.isVideo) videoFolders else folders).readyItems()
            .map { all -> all.find { it.path == parent.folderPath }?.name }
        PlaybackParent.Videos, PlaybackParent.RecentlyPlayed, PlaybackParent.MostPlayed -> flowOf(null)
    }.distinctUntilChanged()

    /**
     * The ids in Favorites, for anything that only needs to know whether a track is one.
     *
     * A set rather than a list of tracks: the player asks this about a single track on every song
     * change, and the playlist screen already reads the tracks themselves in order.
     */
    val favoriteTrackIds: StateFlow<Set<Long>> = playlistDao.observeTrackIds(FAVORITES_PLAYLIST_ID)
        .map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * The collections favorited as themselves - albums, artists, genres, folders and playlists - the latest
     * favorited first, for Favorites to list one tap away. A favorited track is in Favorites' playlist instead.
     */
    val favoriteCollections: StateFlow<List<PlaybackParent>> = favoriteCollectionDao.observeAll()
        .map { keys -> keys.mapNotNull(::playbackParentOf) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun setFavorite(trackId: Long, isFavorite: Boolean) {
        setFavorites(listOf(trackId), emptyList(), isFavorite)
    }

    /**
     * Favorites [trackIds] - into Favorites' playlist - and [collections] as themselves, or, while not
     * [isFavorite], takes them all out of Favorites. Favorites' own playlist is never one of [collections].
     */
    suspend fun setFavorites(trackIds: List<Long>, collections: List<PlaybackParent>, isFavorite: Boolean) {
        val changedAt = System.currentTimeMillis()
        val collectionKeys = collections.map { it.toStorageKey() }
        if (isFavorite) {
            if (trackIds.isNotEmpty()) {
                playlistDao.addTracks(FAVORITES_PLAYLIST_ID, membershipsOf(FAVORITES_PLAYLIST_ID, trackIds, changedAt), changedAt)
            }
            if (collectionKeys.isNotEmpty()) {
                // Each a millisecond before the one before it, so collections favorited together are listed -
                // the latest favorited first - in the order they were given.
                favoriteCollectionDao.insert(
                    collectionKeys.mapIndexed { index, key -> FavoriteCollectionEntity(key, changedAt - index) },
                )
            }
        } else {
            if (trackIds.isNotEmpty()) playlistDao.removeTracks(FAVORITES_PLAYLIST_ID, trackIds, changedAt)
            if (collectionKeys.isNotEmpty()) favoriteCollectionDao.delete(collectionKeys)
        }
    }

    // endregion

    private fun playlistTarget(playlistId: Long) = SortTarget(SortableList.PLAYLIST_TRACKS, playlistId.toString())

    /**
     * [trackIds] as they join [playlistId] at [addedAt] - each a millisecond after the one before, so
     * tracks added together keep the order they were added in wherever a playlist is sorted by date.
     */
    private fun membershipsOf(playlistId: Long, trackIds: List<Long>, addedAt: Long): List<PlaylistTrackEntity> =
        trackIds.mapIndexed { index, trackId -> PlaylistTrackEntity(playlistId, trackId, addedAt + index) }

    /**
     * A collection's rows as [spec]'s entries, each where [instanceId]'s hand-made order puts it - read
     * alongside the rows, so a drop re-sorts the list as soon as it is stored - sorted the way the list
     * is set to.
     */
    private fun <R> Flow<List<R>>.arrangedFor(
        spec: SortSpec<ListEntry>,
        instanceId: String,
        toEntry: (R) -> ListEntry,
    ): Flow<List<ListEntry>> =
        combine(this, arrangementDao.observe(spec.list, instanceId), ::Pair)
            .sortedFor(spec, instanceId) { (rows, arranged) ->
                val positions = arranged.associate { it.trackId to it.position }
                rows.map { row -> toEntry(row).let { entry -> entry.copy(position = positions[entry.track.id]) } }
            }

    private fun Flow<List<ListEntry>>.asTrackContent(): Flow<LibraryContent<Track>> =
        map { entries -> entries.map { it.track } }.asContent()

    /**
     * Turns rows into [spec]'s items, sorted the way its list is set to, and sorts them again
     * whenever that order or the name-sorting mode changes.
     */
    private fun <R, T> Flow<R>.sortedFor(
        spec: SortSpec<T>,
        // Which list of its kind this is, for the lists there can be many of - so one playlist's order
        // is its own. The library's own lists are the only one of their kind and name nothing here.
        instanceId: String? = null,
        toItems: (R) -> List<T>,
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

    /** [spec]'s sections under its list's current order, starting from the order already in memory. */
    private fun <T> sectionsOf(spec: SortSpec<T>): StateFlow<(T) -> String?> {
        val target = SortTarget(spec.list, null)
        fun sectionsFor(order: SortOrder, intelligent: Boolean): (T) -> String? =
            { item -> spec.section(item, order, intelligent) }
        return combine(sortOrders.order(target), intelligentSorting, ::sectionsFor)
            .stateIn(scope, SharingStarted.Eagerly, sectionsFor(sortOrders.currentOrder(target), intelligentSorting.value))
    }

    /**
     * The folders holding music - or, where [isVideo], videos - with the count and cover each row shows.
     * Aggregated by SQLite (`GROUP BY folderPath`), not by grouping the track list in memory.
     */
    private fun foldersOf(isVideo: Boolean): StateFlow<LibraryContent<Folder>> =
        trackDao.observeFolders(isVideo)
            .combine(trackDao.observeFolderCoverArt(isVideo)) { rows, coverRows ->
                val coversByPath = coverRows.groupBy { it.path }
                rows.map { row ->
                    row.toDomain(
                        coverArtUris = rankedCoverArtUris(
                            coversByPath[row.path].orEmpty().associate { it.coverArtUri to it.trackCount },
                        ),
                        isVideo = isVideo,
                    )
                }
            }
            .sortedFor(LibrarySortSpecs.folders) { rows -> rows }
            .shareContent()

    /** The rows once read - a missing collection is then really missing, not merely not loaded yet. */
    private fun <T> Flow<LibraryContent<T>>.readyItems(): Flow<List<T>> = mapNotNull { it.itemsOrNull }

    private fun <T> Flow<List<T>>.shareContent(): StateFlow<LibraryContent<T>> =
        map { LibraryContent.Ready(it) as LibraryContent<T> }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, LibraryContent.Loading)

    private fun <T> Flow<List<T>>.asContent(): Flow<LibraryContent<T>> =
        map { LibraryContent.Ready(it) as LibraryContent<T> }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
}
