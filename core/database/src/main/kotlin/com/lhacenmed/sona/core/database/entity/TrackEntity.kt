package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lhacenmed.sona.core.model.Track

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["path"], unique = true)],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val isFavorite: Boolean = false,
)

fun TrackEntity.toDomain() = Track(
    id = id,
    mediaStoreId = mediaStoreId,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    genre = genre,
    genreId = genreId,
    path = path,
    folderPath = folderPath,
    durationMs = durationMs,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    dateAddedSeconds = dateAddedSeconds,
    coverArtUri = coverArtUri,
    isManuallyScanned = isManuallyScanned,
    isFavorite = isFavorite,
)

fun Track.toEntity() = TrackEntity(
    id = id,
    mediaStoreId = mediaStoreId,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    genre = genre,
    genreId = genreId,
    path = path,
    folderPath = folderPath,
    durationMs = durationMs,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    dateAddedSeconds = dateAddedSeconds,
    coverArtUri = coverArtUri,
    isManuallyScanned = isManuallyScanned,
    isFavorite = isFavorite,
)
