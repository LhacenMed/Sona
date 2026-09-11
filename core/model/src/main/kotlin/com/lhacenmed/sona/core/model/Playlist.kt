package com.lhacenmed.sona.core.model

/**
 * A user-ordered list of tracks.
 *
 * [isBuiltIn] is Favourites: it appears first and cannot be renamed or deleted, but is otherwise an
 * ordinary playlist - it takes folders, exports to M3U and reorders by dragging like any other.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean,
    val trackCount: Int,
)
