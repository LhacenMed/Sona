package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Hands everything the user made of one track to another - the same file, moved or renamed, under its new path's
 * id: its playlist entries, its plays, where it was left, its place in every hand-made order, its queue entries,
 * and the playlist covers it is. A row the new track already has is kept, and the old one goes with its track.
 */
@Dao
interface TrackMoveDao {

    @Transaction
    suspend fun move(fromTrackId: Long, toTrackId: Long) {
        movePlaylistEntries(fromTrackId, toTrackId)
        movePlayStats(fromTrackId, toTrackId)
        moveResumePosition(fromTrackId, toTrackId)
        moveArrangements(fromTrackId, toTrackId)
        moveQueueItems(fromTrackId, toTrackId)
        movePlaylistCovers(fromTrackId, toTrackId)
    }

    @Query("UPDATE OR IGNORE playlist_tracks SET trackId = :toTrackId WHERE trackId = :fromTrackId")
    suspend fun movePlaylistEntries(fromTrackId: Long, toTrackId: Long)

    @Query("UPDATE OR IGNORE play_stats SET trackId = :toTrackId WHERE trackId = :fromTrackId")
    suspend fun movePlayStats(fromTrackId: Long, toTrackId: Long)

    @Query("UPDATE OR IGNORE resume_positions SET trackId = :toTrackId WHERE trackId = :fromTrackId")
    suspend fun moveResumePosition(fromTrackId: Long, toTrackId: Long)

    @Query("UPDATE OR IGNORE arrangements SET trackId = :toTrackId WHERE trackId = :fromTrackId")
    suspend fun moveArrangements(fromTrackId: Long, toTrackId: Long)

    @Query("UPDATE queue_items SET trackId = :toTrackId WHERE trackId = :fromTrackId")
    suspend fun moveQueueItems(fromTrackId: Long, toTrackId: Long)

    @Query("UPDATE playlists SET coverTrackId = :toTrackId WHERE coverTrackId = :fromTrackId")
    suspend fun movePlaylistCovers(fromTrackId: Long, toTrackId: Long)
}
