package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lhacenmed.sona.core.database.entity.HeldLyricsEntity

@Dao
interface HeldLyricsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(files: List<HeldLyricsEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM held_lyrics WHERE path = :path)")
    suspend fun isHeld(path: String): Boolean

    @Query("UPDATE OR REPLACE held_lyrics SET path = :toPath WHERE path = :fromPath")
    suspend fun move(fromPath: String, toPath: String)

    @Query("DELETE FROM held_lyrics WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)
}
