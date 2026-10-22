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
    /** A video file, of which only the sound is played - listed in the Videos tab, never among the music. */
    val isVideo: Boolean,
    /**
     * Every artist the track is credited to, [artistId] - the one [artist] names first - leading: more than
     * one where its credit names several and the library separates them.
     */
    val artistIds: List<Long> = listOf(artistId),
    /** A video's frame, in pixels - 0 for a track, or where it is not known. */
    val width: Int = 0,
    val height: Int = 0,
    /** The file's size - 0 where it is not known. */
    val sizeBytes: Long = 0L,
)
