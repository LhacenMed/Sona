package com.lhacenmed.sona.core.data.playlist

import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.sort.SortOrder

/**
 * A list of tracks as a playlist file carries it: in the order it was shown. One exported from a
 * playlist also says when each track joined it and how it was sorted, so importing it brings the
 * playlist back as it was - an order arranged by hand, and where its new tracks go, included.
 */
data class PlaylistFile(
    val entries: List<Entry>,
    val order: SortOrder? = null,
) {
    data class Entry(
        val track: Track,
        val addedAt: Long? = null,
    )

    companion object {
        /** [tracks] in the order given, with nothing more to say about them. */
        fun of(tracks: List<Track>) = PlaylistFile(tracks.map { Entry(it) })
    }
}
