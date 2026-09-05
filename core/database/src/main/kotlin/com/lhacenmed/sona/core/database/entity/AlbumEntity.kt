package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lhacenmed.sona.core.model.Album

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val coverArtUri: String?,
    val year: Int?,
    val trackCount: Int,
    val dateAddedSeconds: Long,
)

fun AlbumEntity.toDomain() = Album(
    id = id,
    title = title,
    artistId = artistId,
    artistName = artistName,
    coverArtUri = coverArtUri,
    year = year,
    trackCount = trackCount,
    dateAddedSeconds = dateAddedSeconds,
)

fun Album.toEntity() = AlbumEntity(
    id = id,
    title = title,
    artistId = artistId,
    artistName = artistName,
    coverArtUri = coverArtUri,
    year = year,
    trackCount = trackCount,
    dateAddedSeconds = dateAddedSeconds,
)
