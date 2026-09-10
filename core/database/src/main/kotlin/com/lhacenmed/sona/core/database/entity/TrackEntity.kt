package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lhacenmed.sona.core.model.Track

/**
 * A scanned audio file.
 *
 * [id] is **derived from [path]** ([com.lhacenmed.sona.core.database.stableIdOf]) rather than
 * auto-generated. That is a correctness fix, not a micro-optimisation: with `autoGenerate = true`
 * every scanned track arrived with `id = 0`, so `OnConflictStrategy.REPLACE` resolved the unique
 * `path` index by *deleting the existing row and inserting a new one with a fresh rowid*. Every
 * launch therefore rewrote the entire table with brand-new primary keys, which
 *   - invalidated every observing `Flow` and re-rendered all five library tabs from scratch,
 *   - orphaned the persisted playback queue (it stores track ids), and
 *   - forced favourites to be re-overlaid by path on every scan to survive.
 * A path-derived id makes a rescan of unchanged files a no-op instead.
 *
 * The indices back the per-entity queries (album/artist/genre/folder detail screens) so they can
 * read just their own rows instead of loading the whole table and filtering in memory.
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["albumId"]),
        Index(value = ["artistId"]),
        Index(value = ["genreId"]),
        Index(value = ["folderPath"]),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: Long,
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
