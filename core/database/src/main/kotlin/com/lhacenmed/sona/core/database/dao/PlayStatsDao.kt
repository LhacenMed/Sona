package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.lhacenmed.sona.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/** How many tracks Most played holds, [videoCount] of them videos. */
data class MostPlayedCount(
    val trackCount: Int,
    val videoCount: Int,
)

/** How many times one track was listened to through. */
data class TrackPlayCount(
    val trackId: Long,
    val playCount: Int,
)

@Dao
interface PlayStatsDao {

    /** Every track listened to through at least once, with how many times - what lists sorted by play count are ranked by. */
    @Query("SELECT trackId, playCount FROM play_stats WHERE playCount > 0")
    fun observePlayCounts(): Flow<List<TrackPlayCount>>

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

    /** Counts a listen that lasted long enough to mean it - four fifths of the track. */
    @Query(
        """
        INSERT INTO play_stats (trackId, playCount, lastPlayedAt) VALUES (:trackId, 1, :playedAt)
        ON CONFLICT (trackId) DO UPDATE SET playCount = playCount + 1
        """,
    )
    suspend fun recordPlayCounted(trackId: Long, playedAt: Long)

    /** "Recent" - what was played last, most recent first; a track taken out of it, by [forgetLastPlayed], is not. */
    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN play_stats s ON s.trackId = t.id
        WHERE s.lastPlayedAt > 0
        ORDER BY s.lastPlayedAt DESC
        """,
    )
    fun observeRecentlyPlayed(): Flow<List<TrackEntity>>

    /** "Most played" - only tracks listened to far enough to count at least once, videos among them where [includesVideos]. */
    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN play_stats s ON s.trackId = t.id
        WHERE s.playCount > 0 AND (:includesVideos OR t.isVideo = 0)
        ORDER BY s.playCount DESC, s.lastPlayedAt DESC
        """,
    )
    fun observeMostPlayed(includesVideos: Boolean): Flow<List<TrackEntity>>

    /** Takes [trackIds] out of "Recent", their play counts kept - the next time one starts playing puts it back. */
    @Query("DELETE FROM play_stats")
    suspend fun clear()

    @Query("UPDATE play_stats SET lastPlayedAt = 0 WHERE trackId IN (:trackIds)")
    suspend fun forgetLastPlayed(trackIds: List<Long>)

    /** Takes [trackIds] out of "Most played" by counting their listens from zero again. */
    @Query("UPDATE play_stats SET playCount = 0 WHERE trackId IN (:trackIds)")
    suspend fun resetPlayCounts(trackIds: List<Long>)

    @Query(
        """
        SELECT COUNT(*) AS trackCount, COALESCE(SUM(t.isVideo), 0) AS videoCount FROM play_stats s
        INNER JOIN tracks t ON t.id = s.trackId
        WHERE s.playCount > 0 AND (:includesVideos OR t.isVideo = 0)
        """,
    )
    fun observeMostPlayedCount(includesVideos: Boolean): Flow<MostPlayedCount>
}
