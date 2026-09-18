package com.lhacenmed.sona.feature.library.selection

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
