package com.lhacenmed.sona.core.model

/**
 * A user-ordered list of tracks.
 *
 * [isBuiltIn] is Favorites: it appears first and cannot be renamed or deleted, but is otherwise an
 * ordinary playlist - it takes folders, exports to M3U, reorders by dragging and takes a cover like any other.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean,
    val trackCount: Int,
    /** What the cover was chosen to show. */
    val cover: PlaylistCover,
    /**
     * What its cover is drawn from: the one image [cover] names, or - stacked, or with nothing to show
     * for the choice - every distinct cover among the playlist's tracks, the most shared first.
     */
    val coverArtUris: List<String>,
)
