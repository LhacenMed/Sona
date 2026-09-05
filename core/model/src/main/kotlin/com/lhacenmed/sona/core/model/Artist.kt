package com.lhacenmed.sona.core.model

data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val coverArtUri: String?,
)
