package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lhacenmed.sona.core.model.Artist

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val coverArtUris: List<String>,
)

fun ArtistEntity.toDomain() = Artist(
    id = id,
    name = name,
    trackCount = trackCount,
    albumCount = albumCount,
    coverArtUris = coverArtUris,
)

fun Artist.toEntity() = ArtistEntity(
    id = id,
    name = name,
    trackCount = trackCount,
    albumCount = albumCount,
    coverArtUris = coverArtUris,
)
