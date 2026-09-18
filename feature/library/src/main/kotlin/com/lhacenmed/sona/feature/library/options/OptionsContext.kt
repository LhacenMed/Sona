package com.lhacenmed.sona.feature.library.options

/**
 * Where a track's options sheet was opened from - Auxio's `song.xml`/`playlist_song.xml` (LIST),
 * `album_song.xml` (FROM_ALBUM) and `artist_song.xml` (FROM_ARTIST). A track already inside an album
 * or an artist drops the action that would only take it back there.
 *
 * A genre's or a playlist's own track rows have no menu of their own in Auxio - `playlist_song.xml`
 * is `song.xml` again - so [LIST] is also what they, and every other list a track can sit in, use.
 */
enum class TrackOptionsContext {
    LIST,
    FROM_ALBUM,
    FROM_ARTIST,
}

/**
 * Where an album's options sheet was opened from - Auxio's `album.xml` (LIST), `artist_album.xml`
 * (FROM_ARTIST) and `detail_album.xml` (FROM_DETAIL). Inside its own artist the album drops the
 * action that would go there again; on its own detail screen it drops the action that would open it.
 */
enum class AlbumOptionsContext {
    LIST,
    FROM_ARTIST,
    FROM_DETAIL,
}

/**
 * Where an artist's options sheet was opened from - Auxio's `parent.xml` (LIST) and `detail_parent.xml`
 * (FROM_DETAIL), the same pair a genre shares.
 */
enum class ArtistOptionsContext {
    LIST,
    FROM_DETAIL,
}

/** Where a genre's options sheet was opened from - Auxio's `parent.xml` (LIST) and `detail_parent.xml`. */
enum class GenreOptionsContext {
    LIST,
    FROM_DETAIL,
}

/** Where a playlist's options sheet was opened from - Auxio's `playlist.xml` (LIST) and `detail_playlist.xml`. */
enum class PlaylistOptionsContext {
    LIST,
    FROM_DETAIL,
}

/** Where a folder's options were opened from - its row (LIST), or its own screen's menu (FROM_DETAIL). */
enum class FolderOptionsContext {
    LIST,
    FROM_DETAIL,
}
