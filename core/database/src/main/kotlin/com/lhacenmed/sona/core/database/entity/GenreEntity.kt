package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lhacenmed.sona.core.model.Genre

@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val trackCount: Int,
    val artistCount: Int,
    val coverArtUris: List<String>,
)

fun GenreEntity.toDomain() = Genre(
    id = id,
    name = name,
    trackCount = trackCount,
    artistCount = artistCount,
    coverArtUris = coverArtUris,
)

fun Genre.toEntity() = GenreEntity(
    id = id,
    name = name,
    trackCount = trackCount,
    artistCount = artistCount,
    coverArtUris = coverArtUris,
)
