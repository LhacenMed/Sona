package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    /** Every download, the newest first. */
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    /** The download to run next - the longest waiting, one cut off mid-way by the app stopping first. */
    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'RUNNING') ORDER BY createdAt, id LIMIT 1")
    suspend fun next(): DownloadEntity?

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String? = null)

    @Query("UPDATE downloads SET title = :title WHERE id = :id")
    suspend fun setTitle(id: Long, title: String)

    @Query("DELETE FROM downloads WHERE status IN ('DONE', 'FAILED', 'CANCELED')")
    suspend fun deleteFinished()
}
