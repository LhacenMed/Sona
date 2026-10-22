package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.lhacenmed.sona.core.database.entity.LyricsIndexEntity

/** A track's file as its lyrics were last read from it - see [LyricsIndexEntity.signature]. */
data class LyricsIndexSignature(val trackId: Long, val signature: Long)

/** A track found by its lyrics, with them as shown. */
data class LyricsIndexHit(val trackId: Long, val lyrics: String)

@Dao
interface LyricsIndexDao {

    @Query("SELECT rowid AS trackId, signature FROM lyrics_index")
    suspend fun signatures(): List<LyricsIndexSignature>

    /** The tracks whose lyrics hold what [match] - a full-text query - asks for. */
    @Query("SELECT rowid AS trackId, lyrics FROM lyrics_index WHERE lyrics_index MATCH :match")
    suspend fun search(match: String): List<LyricsIndexHit>

    @Query("DELETE FROM lyrics_index WHERE rowid IN (:trackIds)")
    suspend fun delete(trackIds: List<Long>)

    @Insert
    suspend fun insert(rows: List<LyricsIndexEntity>)

    /** Puts [rows] in place of whatever their tracks had. A full-text table holds no key to replace on, so this deletes first. */
    @Transaction
    suspend fun replace(rows: List<LyricsIndexEntity>) {
        delete(rows.map { it.trackId })
        insert(rows)
    }
}
