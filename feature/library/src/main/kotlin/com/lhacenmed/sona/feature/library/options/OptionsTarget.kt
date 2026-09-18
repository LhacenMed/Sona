package com.lhacenmed.sona.feature.library.options

import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.options.OptionsAction.ADD_COLLECTIONS
import com.lhacenmed.sona.feature.library.options.OptionsAction.ADD_TRACKS
import com.lhacenmed.sona.feature.library.options.OptionsAction.ALBUM_DETAILS
import com.lhacenmed.sona.feature.library.options.OptionsAction.ARTIST_DETAILS
import com.lhacenmed.sona.feature.library.options.OptionsAction.DELETE
import com.lhacenmed.sona.feature.library.options.OptionsAction.EDIT
import com.lhacenmed.sona.feature.library.options.OptionsAction.EXCLUDE
import com.lhacenmed.sona.feature.library.options.OptionsAction.EXPORT
import com.lhacenmed.sona.feature.library.options.OptionsAction.IMPORT
import com.lhacenmed.sona.feature.library.options.OptionsAction.PLAY
import com.lhacenmed.sona.feature.library.options.OptionsAction.PLAYLIST_ADD
import com.lhacenmed.sona.feature.library.options.OptionsAction.PLAY_NEXT
import com.lhacenmed.sona.feature.library.options.OptionsAction.QUEUE_ADD
import com.lhacenmed.sona.feature.library.options.OptionsAction.SHARE
import com.lhacenmed.sona.feature.library.options.OptionsAction.SHUFFLE
import com.lhacenmed.sona.feature.library.options.OptionsAction.SONG_PROPERTIES
import com.lhacenmed.sona.feature.library.options.OptionsAction.VIEW_DETAILS
import com.lhacenmed.sona.feature.library.pluralCount
import com.lhacenmed.sona.feature.library.trackCountLabel

/**
 * What an options sheet is showing options for, and where it was opened from - Auxio's `Menu.ForSong`,
 * `ForAlbum`, `ForArtist`, `ForGenre` and `ForPlaylist`.
 *
 * The context defaults to [TrackOptionsContext.LIST] and its siblings: the plain, most-permissive menu
 * a row reaches when nothing about where it sits narrows the choices further.
 */
sealed interface OptionsTarget {
    /**
     * [queueSource] and [queueParent] are the list [track] was opened from and what it plays as -
     * a bare list and no parent for a track with no list around it. Playing this track plays this
     * list from here, the same as tapping the row does; Play next, Add to queue and Add to playlist
     * only ever touch [track] itself, whatever list it came from.
     */
    data class ForTrack(
        val track: Track,
        val context: TrackOptionsContext = TrackOptionsContext.LIST,
        val queueSource: List<Track> = listOf(track),
        val queueParent: PlaybackParent? = null,
    ) : OptionsTarget

    data class ForAlbum(
        val album: Album,
        val context: AlbumOptionsContext = AlbumOptionsContext.LIST,
    ) : OptionsTarget

    data class ForArtist(
        val artist: Artist,
        val context: ArtistOptionsContext = ArtistOptionsContext.LIST,
    ) : OptionsTarget

    data class ForGenre(
        val genre: Genre,
        val context: GenreOptionsContext = GenreOptionsContext.LIST,
    ) : OptionsTarget

    data class ForPlaylist(
        val playlist: Playlist,
        val context: PlaylistOptionsContext = PlaylistOptionsContext.LIST,
    ) : OptionsTarget

    /** A folder - Sona's own, as Auxio has no folders: a collection like an album, that can also be excluded. */
    data class ForFolder(
        val folder: Folder,
        val context: FolderOptionsContext = FolderOptionsContext.LIST,
    ) : OptionsTarget

    /** A selection, as the tracks it stands for, in the order their rows were selected - Auxio's `Menu.ForSelection`. */
    data class ForSelection(val tracks: List<Track>) : OptionsTarget
}

/**
 * What a collection offers - one list each, which is both its row's options sheet and its own screen's
 * menu, so the two can never drift apart. Its own screen leaves out View, which would only open the
 * screen already showing, as Auxio's `detail_*` menus do.
 *
 * A collection is never shared as a whole: sharing is for tracks, one at a time or a selection of them.
 */
private val AlbumActions = listOf(PLAY, SHUFFLE, VIEW_DETAILS, PLAY_NEXT, QUEUE_ADD, PLAYLIST_ADD, ARTIST_DETAILS, EXPORT)
private val ArtistActions = listOf(PLAY, SHUFFLE, VIEW_DETAILS, PLAY_NEXT, QUEUE_ADD, PLAYLIST_ADD, EXPORT)
private val GenreActions = listOf(PLAY, SHUFFLE, VIEW_DETAILS, PLAY_NEXT, QUEUE_ADD, PLAYLIST_ADD, EXPORT)
private val PlaylistActions =
    listOf(PLAY, SHUFFLE, VIEW_DETAILS, PLAY_NEXT, QUEUE_ADD, ADD_TRACKS, ADD_COLLECTIONS, EDIT, IMPORT, EXPORT, DELETE)
private val FolderActions = listOf(PLAY, SHUFFLE, VIEW_DETAILS, PLAY_NEXT, QUEUE_ADD, PLAYLIST_ADD, EXPORT, EXCLUDE)

/** The rows an options sheet or a collection's menu lists, top to bottom - Auxio's inflated menu XML, chosen by target and context. */
fun OptionsTarget.actions(): List<OptionsAction> = when (this) {
    is OptionsTarget.ForTrack -> when (context) {
        TrackOptionsContext.LIST ->
            listOf(PLAY, SHUFFLE, PLAY_NEXT, QUEUE_ADD, PLAYLIST_ADD, ARTIST_DETAILS, ALBUM_DETAILS, SONG_PROPERTIES, SHARE)
        TrackOptionsContext.FROM_ALBUM ->
            listOf(PLAY, SHUFFLE, PLAY_NEXT, QUEUE_ADD, PLAYLIST_ADD, ARTIST_DETAILS, SONG_PROPERTIES, SHARE)
        TrackOptionsContext.FROM_ARTIST ->
            listOf(PLAY, SHUFFLE, PLAY_NEXT, QUEUE_ADD, PLAYLIST_ADD, ALBUM_DETAILS, SONG_PROPERTIES, SHARE)
    }

    is OptionsTarget.ForAlbum -> when (context) {
        AlbumOptionsContext.LIST -> AlbumActions
        AlbumOptionsContext.FROM_ARTIST -> AlbumActions - ARTIST_DETAILS
        AlbumOptionsContext.FROM_DETAIL -> AlbumActions - VIEW_DETAILS
    }

    is OptionsTarget.ForArtist -> when (context) {
        ArtistOptionsContext.LIST -> ArtistActions
        ArtistOptionsContext.FROM_DETAIL -> ArtistActions - VIEW_DETAILS
    }

    is OptionsTarget.ForGenre -> when (context) {
        GenreOptionsContext.LIST -> GenreActions
        GenreOptionsContext.FROM_DETAIL -> GenreActions - VIEW_DETAILS
    }

    is OptionsTarget.ForPlaylist -> when (context) {
        PlaylistOptionsContext.LIST -> PlaylistActions
        PlaylistOptionsContext.FROM_DETAIL -> PlaylistActions - VIEW_DETAILS
    }

    is OptionsTarget.ForFolder -> when (context) {
        FolderOptionsContext.LIST -> FolderActions
        FolderOptionsContext.FROM_DETAIL -> FolderActions - VIEW_DETAILS
    }

    is OptionsTarget.ForSelection -> listOf(PLAY, SHUFFLE, PLAY_NEXT, QUEUE_ADD, PLAYLIST_ADD, SHARE)
}

/**
 * The actions listed but not clickable - Auxio's `getDisabledItemIds`: an artist or a playlist with no
 * tracks yet cannot be played, queued or exported, though it is still worth seeing and still worth
 * editing or deleting. A track, an album and a genre are never disabled this way.
 *
 * Favorites, which Auxio has no counterpart for, can never be deleted - the rule the
 * playlists screen's selection bar already keeps.
 */
fun OptionsTarget.disabledActions(): Set<OptionsAction> = when (this) {
    is OptionsTarget.ForArtist -> if (artist.trackCount == 0) {
        setOf(PLAY, SHUFFLE, PLAY_NEXT, QUEUE_ADD, PLAYLIST_ADD, EXPORT)
    } else {
        emptySet()
    }

    is OptionsTarget.ForPlaylist -> buildSet {
        if (playlist.isBuiltIn) add(DELETE)
        if (playlist.trackCount == 0) addAll(listOf(PLAY, SHUFFLE, PLAY_NEXT, QUEUE_ADD, EXPORT))
    }

    is OptionsTarget.ForTrack,
    is OptionsTarget.ForAlbum,
    is OptionsTarget.ForGenre,
    is OptionsTarget.ForFolder,
    is OptionsTarget.ForSelection,
    -> emptySet()
}

/** What kind of thing the sheet is showing options for - Auxio's `lbl_song`/`lbl_album`/etc, over the name. */
fun OptionsTarget.typeLabel(): String = when (this) {
    is OptionsTarget.ForTrack -> "Track"
    is OptionsTarget.ForAlbum -> "Album"
    is OptionsTarget.ForArtist -> "Artist"
    is OptionsTarget.ForGenre -> "Genre"
    is OptionsTarget.ForPlaylist -> "Playlist"
    is OptionsTarget.ForFolder -> "Folder"
    is OptionsTarget.ForSelection -> "Selection"
}

/** The sheet's headline - Auxio's `menuName`. */
fun OptionsTarget.name(): String = when (this) {
    is OptionsTarget.ForTrack -> track.title
    is OptionsTarget.ForAlbum -> album.title
    is OptionsTarget.ForArtist -> artist.name
    is OptionsTarget.ForGenre -> genre.name
    is OptionsTarget.ForPlaylist -> playlist.name
    is OptionsTarget.ForFolder -> folder.name
    is OptionsTarget.ForSelection -> pluralCount(tracks.size, "track")
}

/** The line under the name - Auxio's `menuInfo`. */
fun OptionsTarget.infoLine(): String = when (this) {
    is OptionsTarget.ForTrack -> track.artist
    is OptionsTarget.ForAlbum -> album.artistName
    is OptionsTarget.ForArtist ->
        albumCountLabel(artist.albumCount) + COUNTS_SEPARATOR + trackCountLabel(artist.trackCount)
    is OptionsTarget.ForGenre ->
        artistCountLabel(genre.artistCount) + COUNTS_SEPARATOR + trackCountLabel(genre.trackCount)
    is OptionsTarget.ForPlaylist -> trackCountLabel(playlist.trackCount)
    is OptionsTarget.ForFolder -> trackCountLabel(folder.trackCount)
    is OptionsTarget.ForSelection -> formatDurationMs(tracks.sumOf { it.durationMs })
}

/** What separates the two counts under an artist or a genre: Auxio's `fmt_two`. */
private const val COUNTS_SEPARATOR = " • "

/** A count of albums, or Auxio's `def_album_count` for one that holds none. */
private fun albumCountLabel(count: Int): String = if (count == 0) "No albums" else pluralCount(count, "album")

/** A count of artists, or Auxio's `def_artist_count` for one that holds none. */
private fun artistCountLabel(count: Int): String = if (count == 0) "No artists" else pluralCount(count, "artist")
