package com.lhacenmed.sona.core.model.sort

/**
 * Every list in the app the user can sort.
 *
 * Only the list's identity: what it can be sorted by, and how each choice orders it, belong to the
 * data layer - the one place that actually sorts. Detail lists are one entry per *kind* rather than
 * per album or playlist, so the order chosen in one playlist is the order every playlist opens in.
 */
enum class SortableList {
    TRACKS,
    ALBUMS,
    ARTISTS,
    GENRES,
    FOLDERS,
    PLAYLISTS,
    ALBUM_TRACKS,
    ARTIST_TRACKS,
    GENRE_TRACKS,
    FOLDER_TRACKS,
    PLAYLIST_TRACKS,
}
