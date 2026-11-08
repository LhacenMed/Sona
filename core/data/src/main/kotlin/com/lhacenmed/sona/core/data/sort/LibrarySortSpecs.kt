package com.lhacenmed.sona.core.data.sort

import com.lhacenmed.sona.core.common.sort.SortField
import com.lhacenmed.sona.core.database.dao.PlaylistWithCount
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.CollectionStats
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.UnknownNames
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortableList

/**
 * A track as a list holds it: where the user dragged it to - null until a collection is arranged, or
 * when the track joined it since - when it joined: a playlist, when it was added to it; any other
 * list, when its file joined the library - and how many times it was listened to through.
 */
internal data class ListEntry(
    val track: Track,
    val addedAt: Long = track.dateAddedSeconds,
    val position: Int? = null,
    val playCount: Int = 0,
)

/**
 * What every sortable list can be sorted by.
 *
 * Only what makes sense for the list is offered: an artist's tracks all share one artist, so that list
 * has no "Artist"; an album's tracks have a real order of their own, so that list has "Track number";
 * only a playlist has a second date - when a track joined it. Every collection's tracks can be put in
 * an order of the user's own by dragging, so each of those lists offers Custom too. Every list offers
 * Name, Duration, Date added and Play count: a collection's are what its tracks add up to - see
 * [CollectionStats].
 *
 * Every ordering ends in a name or the album's own order, so items that tie on what was chosen - two
 * albums from one year - still come out in one defined order rather than in whatever order the
 * database returned them.
 */
internal object LibrarySortSpecs {

    private val nameAscending = SortOrder(SortCriterion.NAME, SortDirection.ASCENDING)

    // region Tracks

    private val trackTitle = SortField.Name<Track>({ it.title })
    private val trackArtist = SortField.Name<Track>({ it.artist }, { it.artist == UnknownNames.ARTIST })
    private val trackAlbum = SortField.Name<Track>({ it.album }, { it.album == UnknownNames.ALBUM })
    private val trackYear = SortField.Number<Track>(NumberSection) { it.year?.toLong() }
    private val trackDuration = SortField.Number<Track>(::durationSection) { it.durationMs }
    private val trackDateAdded = SortField.Number<Track>(::monthOfEpochSecondsSection) { it.dateAddedSeconds }
    private val trackDisc = SortField.Number<Track> { it.discNumber?.toLong() }
    private val trackNumber = SortField.Number<Track> { it.trackNumber?.toLong() }

    /** The order an album plays in, which is also how tracks grouped under one album stay in order. */
    private val trackAlbumOrder: List<SortField<Track>> = listOf(trackDisc, trackNumber, trackTitle)

    private val tracksByTitle: List<SortField<Track>> = listOf(trackTitle)
    private val tracksByArtist: List<SortField<Track>> = listOf(trackArtist, trackAlbum) + trackAlbumOrder
    private val tracksByAlbum: List<SortField<Track>> = listOf(trackAlbum) + trackAlbumOrder
    private val tracksByYear: List<SortField<Track>> = listOf(trackYear, trackAlbum) + trackAlbumOrder
    private val tracksByDuration: List<SortField<Track>> = listOf(trackDuration, trackTitle)
    private val tracksByDateAdded: List<SortField<Track>> = listOf(trackDateAdded, trackTitle)

    /** Most listened to first once reversed, ties by title. */
    private val entriesByPlayCount: List<SortField<ListEntry>> =
        listOf(SortField.Number<ListEntry>(NumberSection) { it.playCount.toLong() }) + tracksByTitle.ofEntry()

    /** How every collection's tracks are arranged by hand - see [Arrangement]. */
    private val entryArrangement = Arrangement<ListEntry>(position = { it.position }, addedAt = { it.addedAt })

    val tracks = SortSpec(
        list = SortableList.TRACKS,
        default = SortOrder(SortCriterion.DATE_ADDED, SortDirection.DESCENDING),
        orderings = mapOf(
            SortCriterion.NAME to tracksByTitle.ofEntry(),
            SortCriterion.ARTIST to tracksByArtist.ofEntry(),
            SortCriterion.ALBUM to tracksByAlbum.ofEntry(),
            SortCriterion.YEAR to tracksByYear.ofEntry(),
            SortCriterion.DURATION to tracksByDuration.ofEntry(),
            SortCriterion.DATE_ADDED to tracksByDateAdded.ofEntry(),
            SortCriterion.PLAY_COUNT to entriesByPlayCount,
        ),
    )

    /** The Videos tab: named, timed and dated as tracks are - a video has no album, year or track number. */
    val videos = SortSpec(
        list = SortableList.VIDEOS,
        default = SortOrder(SortCriterion.DATE_ADDED, SortDirection.DESCENDING),
        orderings = mapOf(
            SortCriterion.NAME to tracksByTitle.ofEntry(),
            SortCriterion.DURATION to tracksByDuration.ofEntry(),
            SortCriterion.DATE_ADDED to tracksByDateAdded.ofEntry(),
            SortCriterion.PLAY_COUNT to entriesByPlayCount,
        ),
    )

    val albumTracks = SortSpec(
        list = SortableList.ALBUM_TRACKS,
        default = SortOrder(SortCriterion.TRACK_NUMBER, SortDirection.ASCENDING),
        orderings = mapOf(
            SortCriterion.TRACK_NUMBER to trackAlbumOrder.ofEntry(),
            SortCriterion.NAME to tracksByTitle.ofEntry(),
            SortCriterion.DURATION to tracksByDuration.ofEntry(),
            SortCriterion.DATE_ADDED to tracksByDateAdded.ofEntry(),
            SortCriterion.PLAY_COUNT to entriesByPlayCount,
        ),
        arrangement = entryArrangement,
    )

    val artistTracks = SortSpec(
        list = SortableList.ARTIST_TRACKS,
        default = nameAscending,
        orderings = mapOf(
            SortCriterion.NAME to tracksByTitle.ofEntry(),
            SortCriterion.ALBUM to tracksByAlbum.ofEntry(),
            SortCriterion.YEAR to tracksByYear.ofEntry(),
            SortCriterion.DURATION to tracksByDuration.ofEntry(),
            SortCriterion.DATE_ADDED to tracksByDateAdded.ofEntry(),
            SortCriterion.PLAY_COUNT to entriesByPlayCount,
        ),
        arrangement = entryArrangement,
    )

    val genreTracks = SortSpec(
        list = SortableList.GENRE_TRACKS,
        default = nameAscending,
        orderings = mapOf(
            SortCriterion.NAME to tracksByTitle.ofEntry(),
            SortCriterion.ARTIST to tracksByArtist.ofEntry(),
            SortCriterion.ALBUM to tracksByAlbum.ofEntry(),
            SortCriterion.YEAR to tracksByYear.ofEntry(),
            SortCriterion.DURATION to tracksByDuration.ofEntry(),
            SortCriterion.DATE_ADDED to tracksByDateAdded.ofEntry(),
            SortCriterion.PLAY_COUNT to entriesByPlayCount,
        ),
        arrangement = entryArrangement,
    )

    val folderTracks = SortSpec(
        list = SortableList.FOLDER_TRACKS,
        default = nameAscending,
        orderings = mapOf(
            SortCriterion.NAME to tracksByTitle.ofEntry(),
            SortCriterion.ARTIST to tracksByArtist.ofEntry(),
            SortCriterion.ALBUM to tracksByAlbum.ofEntry(),
            SortCriterion.YEAR to tracksByYear.ofEntry(),
            SortCriterion.DURATION to tracksByDuration.ofEntry(),
            SortCriterion.DATE_ADDED to tracksByDateAdded.ofEntry(),
            SortCriterion.PLAY_COUNT to entriesByPlayCount,
        ),
        arrangement = entryArrangement,
    )

    /**
     * A playlist's tracks. "Date" is the track's own - when its file joined the library - and "Date
     * added" is when it joined this playlist, which is the reading that name has everywhere: when the
     * item joined the list it is shown in.
     */
    val playlistTracks = SortSpec(
        list = SortableList.PLAYLIST_TRACKS,
        default = SortOrder(SortCriterion.DATE_ADDED, SortDirection.DESCENDING),
        orderings = mapOf(
            SortCriterion.NAME to tracksByTitle.ofEntry(),
            SortCriterion.ARTIST to tracksByArtist.ofEntry(),
            SortCriterion.ALBUM to tracksByAlbum.ofEntry(),
            SortCriterion.DURATION to tracksByDuration.ofEntry(),
            SortCriterion.DATE to tracksByDateAdded.ofEntry(),
            SortCriterion.DATE_ADDED to
                listOf(SortField.Number<ListEntry> { it.addedAt }) + tracksByTitle.ofEntry(),
            SortCriterion.PLAY_COUNT to entriesByPlayCount,
        ),
        arrangement = entryArrangement,
    )

    /** The same track orderings, read through the entry that holds the track. */
    private fun List<SortField<Track>>.ofEntry(): List<SortField<ListEntry>> = map { field ->
        when (field) {
            is SortField.Name -> SortField.Name<ListEntry>(
                { entry -> field.read(entry.track) },
                { entry -> field.isPlaceholder(entry.track) },
            )
            is SortField.Number -> SortField.Number<ListEntry> { entry -> field.read(entry.track) }
        }
    }

    // endregion

    // region Collections

    /** What a collection's tracks add up to, as each orders a list of collections - see [CollectionStats]. */
    private fun <T> byDuration(stats: (T) -> CollectionStats) = SortField.Number<T>(::durationSection) { stats(it).durationMs }
    private fun <T> byDateAdded(stats: (T) -> CollectionStats) =
        SortField.Number<T>(::monthOfEpochSecondsSection) { stats(it).dateAddedSeconds }
    private fun <T> byPlayCount(stats: (T) -> CollectionStats) = SortField.Number<T>(NumberSection) { stats(it).playCount.toLong() }

    private val albumTitle = SortField.Name<Album>({ it.title }, { it.title == UnknownNames.ALBUM })

    val albums = SortSpec(
        list = SortableList.ALBUMS,
        default = nameAscending,
        orderings = mapOf(
            SortCriterion.NAME to listOf(albumTitle),
            SortCriterion.ARTIST to listOf(
                SortField.Name<Album>({ it.artistName }, { it.artistName == UnknownNames.ARTIST }),
                SortField.Number { it.year?.toLong() },
                albumTitle,
            ),
            SortCriterion.YEAR to listOf(SortField.Number<Album>(NumberSection) { it.year?.toLong() }, albumTitle),
            SortCriterion.TRACK_COUNT to
                listOf(SortField.Number<Album>(NumberSection) { it.trackCount.toLong() }, albumTitle),
            SortCriterion.DURATION to listOf(byDuration(Album::stats), albumTitle),
            SortCriterion.DATE_ADDED to
                listOf(SortField.Number<Album>(::monthOfEpochSecondsSection) { it.dateAddedSeconds }, albumTitle),
            SortCriterion.PLAY_COUNT to listOf(byPlayCount(Album::stats), albumTitle),
        ),
    )

    private val artistName = SortField.Name<Artist>({ it.name }, { it.name == UnknownNames.ARTIST })

    val artists = SortSpec(
        list = SortableList.ARTISTS,
        default = nameAscending,
        orderings = mapOf(
            SortCriterion.NAME to listOf(artistName),
            SortCriterion.ALBUM_COUNT to
                listOf(SortField.Number<Artist>(NumberSection) { it.albumCount.toLong() }, artistName),
            SortCriterion.TRACK_COUNT to
                listOf(SortField.Number<Artist>(NumberSection) { it.trackCount.toLong() }, artistName),
            SortCriterion.DURATION to listOf(byDuration(Artist::stats), artistName),
            SortCriterion.DATE_ADDED to listOf(byDateAdded(Artist::stats), artistName),
            SortCriterion.PLAY_COUNT to listOf(byPlayCount(Artist::stats), artistName),
        ),
    )

    private val genreName = SortField.Name<Genre>({ it.name }, { it.name == UnknownNames.GENRE })

    val genres = SortSpec(
        list = SortableList.GENRES,
        default = nameAscending,
        orderings = mapOf(
            SortCriterion.NAME to listOf(genreName),
            SortCriterion.TRACK_COUNT to
                listOf(SortField.Number<Genre>(NumberSection) { it.trackCount.toLong() }, genreName),
            SortCriterion.DURATION to listOf(byDuration(Genre::stats), genreName),
            SortCriterion.DATE_ADDED to listOf(byDateAdded(Genre::stats), genreName),
            SortCriterion.PLAY_COUNT to listOf(byPlayCount(Genre::stats), genreName),
        ),
    )

    private val folderName = SortField.Name<Folder>({ it.name })

    /** Every folder - of music and of videos alike, which are sorted one way wherever they are listed. */
    val folders = SortSpec(
        list = SortableList.FOLDERS,
        default = nameAscending,
        orderings = mapOf(
            SortCriterion.NAME to listOf(folderName),
            SortCriterion.TRACK_COUNT to
                listOf(SortField.Number<Folder>(NumberSection) { it.trackCount.toLong() }, folderName),
            SortCriterion.DURATION to listOf(byDuration(Folder::stats), folderName),
            SortCriterion.DATE_ADDED to listOf(byDateAdded(Folder::stats), folderName),
            SortCriterion.PLAY_COUNT to listOf(byPlayCount(Folder::stats), folderName),
        ),
    )

    private val playlistName = SortField.Name<PlaylistWithCount>({ it.name })

    /**
     * "Date" is when the playlist last changed - made, renamed, or its tracks added, removed or moved - and
     * "Date added" when it was made.
     */
    val playlists = SortSpec(
        list = SortableList.PLAYLISTS,
        default = SortOrder(SortCriterion.DATE, SortDirection.DESCENDING),
        orderings = mapOf(
            SortCriterion.NAME to listOf(playlistName),
            SortCriterion.TRACK_COUNT to
                listOf(SortField.Number<PlaylistWithCount> { it.trackCount.toLong() }, playlistName),
            SortCriterion.DATE to listOf(SortField.Number<PlaylistWithCount> { it.modifiedAt }, playlistName),
            SortCriterion.DURATION to listOf(SortField.Number<PlaylistWithCount> { it.durationMs }, playlistName),
            SortCriterion.DATE_ADDED to listOf(SortField.Number<PlaylistWithCount> { it.createdAt }, playlistName),
            SortCriterion.PLAY_COUNT to listOf(SortField.Number<PlaylistWithCount> { it.playCount.toLong() }, playlistName),
        ),
    )

    // endregion

    fun of(list: SortableList): SortSpec<*> = when (list) {
        SortableList.TRACKS -> tracks
        SortableList.ALBUMS -> albums
        SortableList.ARTISTS -> artists
        SortableList.GENRES -> genres
        SortableList.FOLDERS -> folders
        SortableList.VIDEOS -> videos
        SortableList.PLAYLISTS -> playlists
        SortableList.ALBUM_TRACKS -> albumTracks
        SortableList.ARTIST_TRACKS -> artistTracks
        SortableList.GENRE_TRACKS -> genreTracks
        SortableList.FOLDER_TRACKS -> folderTracks
        SortableList.PLAYLIST_TRACKS -> playlistTracks
    }
}
