package com.lhacenmed.sona.core.model

data class Genre(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val artistCount: Int,
    /** Every distinct cover among the genre's tracks, the most shared first - what its cover is composed from. */
    val coverArtUris: List<String>,
)
