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
)
