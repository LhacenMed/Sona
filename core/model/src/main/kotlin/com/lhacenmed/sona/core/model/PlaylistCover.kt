package com.lhacenmed.sona.core.model

/** What a playlist's cover shows - chosen in its editor. */
sealed interface PlaylistCover {

    /** The default: the covers of its tracks stacked into a pile. */
    data object Stacked : PlaylistCover

    /** The cover of whichever track the playlist's current sort puts first - it follows every re-sort and reorder. */
    data object FirstTrack : PlaylistCover

    /** The cover of whichever track the playlist's current sort puts last. */
    data object LastTrack : PlaylistCover

    /** The cover of one track, in this playlist or anywhere in the library. */
    data class OfTrack(val trackId: Long) : PlaylistCover

    /** An image from the device, held as the app's own copy once saved. */
    data class Image(val uri: String) : PlaylistCover
}
