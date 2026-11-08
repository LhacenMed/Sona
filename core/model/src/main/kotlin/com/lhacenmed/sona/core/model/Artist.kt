package com.lhacenmed.sona.core.model

data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    /** Every distinct cover among the artist's tracks, the most shared first - what its cover is composed from. */
    val coverArtUris: List<String>,
    /** What its tracks add up to, as the library lists it - see [CollectionStats]. */
    val stats: CollectionStats = CollectionStats(),
)
