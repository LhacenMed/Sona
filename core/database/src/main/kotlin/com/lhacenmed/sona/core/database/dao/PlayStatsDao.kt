package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.lhacenmed.sona.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayStatsDao {

    /**
     * Stamps a track as just played, starting its row if this is the first time.
     *
     * Upsert in one statement rather than read-then-write, so two tracks starting close together
     * cannot lose a count between them.
     */
    @Query(
        """
        INSERT INTO play_stats (trackId, playCount, lastPlayedAt) VALUES (:trackId, 0, :playedAt)
        ON CONFLICT (trackId) DO UPDATE SET lastPlayedAt = :playedAt
        """,
    )
    suspend fun recordPlayStarted(trackId: Long, playedAt: Long)

    /** Counts a listen that lasted long enough to mean it. */
    @Query(
        """
        INSERT INTO play_stats (trackId, playCount, lastPlayedAt) VALUES (:trackId, 1, :playedAt)
        ON CONFLICT (trackId) DO UPDATE SET playCount = playCount + 1
        """,
    )
    suspend fun recordPlayCounted(trackId: Long, playedAt: Long)

    /** "Recent" - what was played last, most recent first. */
    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN play_stats s ON s.trackId = t.id
        ORDER BY s.lastPlayedAt DESC
        """,
    )
    fun observeRecentlyPlayed(): Flow<List<TrackEntity>>

    /** "Most played" - only tracks that got past the threshold at least once. */
    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN play_stats s ON s.trackId = t.id
        WHERE s.playCount > 0
        ORDER BY s.playCount DESC, s.lastPlayedAt DESC
        """,
    )
    fun observeMostPlayed(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM play_stats")
    fun observeRecentlyPlayedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM play_stats WHERE playCount > 0")
    fun observeMostPlayedCount(): Flow<Int>
}
