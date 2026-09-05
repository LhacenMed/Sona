package com.lhacenmed.sona.core.model

data class Genre(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val coverArtUri: String?,
)
