package com.lhacenmed.sona.core.model

/** A runtime aggregation over [Track.folderPath] - not a persisted entity. */
data class Folder(
    val path: String,
    val name: String,
    val trackCount: Int,
    /** Every distinct cover among the folder's tracks, the most shared first - what its cover is composed from. */
    val coverArtUris: List<String>,
)
