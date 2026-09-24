package com.lhacenmed.sona.feature.library.selection

import com.lhacenmed.sona.core.model.Album as AlbumModel
import com.lhacenmed.sona.core.model.Artist as ArtistModel
import com.lhacenmed.sona.core.model.Folder as FolderModel
import com.lhacenmed.sona.core.model.Genre as GenreModel
import com.lhacenmed.sona.core.model.Playlist as PlaylistModel
import com.lhacenmed.sona.core.model.Track as TrackModel

/**
 * What a selected row stands for - Auxio's selected `Music`, by identity rather than by object.
 *
 * Typed, because one selection can mix every kind of row: a track and an album can share an id, and
 * only the key's kind keeps them apart. Identities rather than the models themselves, so a row stays
 * selected when a rescan hands back a changed copy of the same album.
 */
sealed interface SelectionKey {
    data class Track(val trackId: Long) : SelectionKey

    data class Album(val albumId: Long) : SelectionKey

    data class Artist(val artistId: Long) : SelectionKey

    data class Genre(val genreId: Long) : SelectionKey

    data class Playlist(val playlistId: Long) : SelectionKey

    data class Folder(val folderPath: String) : SelectionKey
}

/**
 * The key [item]'s row joins a selection by, or null for one that cannot be selected - an artist or a
 * playlist with no tracks, which would add nothing. One rule for every row and every list, so a list
 * dragged across selects exactly the rows a tap would.
 */
internal fun selectionKeyOf(item: Any?): SelectionKey? = when (item) {
    is TrackModel -> SelectionKey.Track(item.id)
    is AlbumModel -> SelectionKey.Album(item.id)
    is ArtistModel -> SelectionKey.Artist(item.id).takeIf { item.trackCount > 0 }
    is GenreModel -> SelectionKey.Genre(item.id)
    is PlaylistModel -> SelectionKey.Playlist(item.id).takeIf { item.trackCount > 0 }
    is FolderModel -> SelectionKey.Folder(item.path)
    else -> null
}
