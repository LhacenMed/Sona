package com.lhacenmed.sona.core.model

data class Album(
    val id: Long,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val coverArtUri: String?,
    val year: Int?,
    val trackCount: Int,
    val dateAddedSeconds: Long,
    /** Every artist the album is credited to, [artistId] leading - as a track's [Track.artistIds] are. */
    val artistIds: List<Long> = listOf(artistId),
)
