package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.LyricsEntity
import kotlinx.coroutines.flow.Flow

/** Stored lyrics - ArchiveTune's lyrics queries from its `DatabaseDao`. */
@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    fun observeLyrics(trackId: Long): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun getLyrics(trackId: Long): LyricsEntity?

    @Upsert
    suspend fun upsert(lyrics: LyricsEntity)

    /** Leaves a row the track already has as it was - lyrics the user typed in are never overwritten by a read. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics")
    suspend fun clearAll()
}
