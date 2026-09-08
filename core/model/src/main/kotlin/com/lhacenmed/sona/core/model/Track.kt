package com.lhacenmed.sona.core.model

data class Track(
    val id: Long,
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val genre: String?,
    val genreId: Long?,
    val path: String,
    val folderPath: String,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val dateAddedSeconds: Long,
    val coverArtUri: String?,
    val isManuallyScanned: Boolean,
    val isFavorite: Boolean,
)
