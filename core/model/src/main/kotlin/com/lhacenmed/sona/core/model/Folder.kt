package com.lhacenmed.sona.core.model

/** A runtime aggregation over [Track.folderPath] - not a persisted entity. */
data class Folder(
    val path: String,
    val name: String,
    val trackCount: Int,
    /** Every distinct cover among the folder's tracks, the most shared first - what its cover is composed from. */
    val coverArtUris: List<String>,
    /** A folder of the Videos tab - its videos alone - rather than of the Folders tab and its music. */
    val isVideo: Boolean,
    /** What its tracks add up to, as the library lists it - see [CollectionStats]. */
    val stats: CollectionStats = CollectionStats(),
)

/** What playing [this] folder plays from - its music, or its videos. */
val Folder.playbackParent: PlaybackParent get() = PlaybackParent.Folder(path, isVideo)
