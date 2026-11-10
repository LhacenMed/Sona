package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lhacenmed.sona.core.database.entity.DownloadCommandEntity
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.DownloadWatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    /** Every download, the newest first. */
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Insert
    suspend fun insertAll(downloads: List<DownloadEntity>): List<Long>

    /**
     * The downloads to run next, the longest waiting first - one cut off mid-way by the app stopping among them - leaving
     * out [running] and any not to start before a moment after [now].
     */
    @Query(
        "SELECT * FROM downloads WHERE status IN ('QUEUED', 'RUNNING') AND id NOT IN (:running) " +
            "AND (startAt IS NULL OR startAt <= :now) ORDER BY createdAt, id LIMIT :limit",
    )
    suspend fun nextToRun(running: Collection<Long>, now: Long, limit: Int): List<DownloadEntity>

    /** When the soonest download set to start later starts - null with none waiting for a time. */
    @Query("SELECT MIN(startAt) FROM downloads WHERE status = 'QUEUED' AND startAt > :now")
    suspend fun nextStartAt(now: Long): Long?

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun byId(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id IN (:ids)")
    suspend fun byIds(ids: Collection<Long>): List<DownloadEntity>

    @Query("UPDATE downloads SET status = :status, error = :error, failure = :failure WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String? = null, failure: String? = null)

    @Query("UPDATE downloads SET status = 'QUEUED', error = NULL, failure = NULL, startAt = NULL WHERE id IN (:ids)")
    suspend fun requeue(ids: Collection<Long>)

    @Query("UPDATE downloads SET status = 'QUEUED', error = NULL, failure = NULL WHERE status = 'FAILED' AND failure = :failure")
    suspend fun requeueFailed(failure: String)

    /** Lets [id] download as its choices pick, the format it was asked for being no longer offered. */
    @Query("UPDATE downloads SET formatId = NULL WHERE id = :id")
    suspend fun clearFormat(id: Long)

    @Query("UPDATE downloads SET status = 'CANCELED' WHERE id IN (:ids) AND status = 'QUEUED'")
    suspend fun cancelQueued(ids: Collection<Long>)

    /** What the link was found to be, as it is first read. */
    @Query(
        "UPDATE downloads SET title = :title, thumbnailUrl = COALESCE(thumbnailUrl, :thumbnailUrl), " +
            "uploader = COALESCE(uploader, :uploader), site = :site, durationSeconds = :durationSeconds WHERE id = :id",
    )
    suspend fun setFound(id: Long, title: String, thumbnailUrl: String?, uploader: String?, site: String?, durationSeconds: Long?)

    /** What it saved, as it is over. */
    @Query("UPDATE downloads SET savedPaths = :savedPaths, sizeBytes = :sizeBytes, finishedAt = :finishedAt WHERE id = :id")
    suspend fun setSaved(id: Long, savedPaths: String, sizeBytes: Long, finishedAt: Long)

    @Query("DELETE FROM downloads WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<Long>)

    @Query("DELETE FROM downloads WHERE status IN ('DONE', 'FAILED', 'CANCELED')")
    suspend fun deleteFinished()

    @Query("SELECT * FROM download_commands ORDER BY name COLLATE NOCASE")
    fun observeCommands(): Flow<List<DownloadCommandEntity>>

    @Insert
    suspend fun insertCommand(command: DownloadCommandEntity): Long

    @Query("UPDATE download_commands SET name = :name, arguments = :arguments WHERE id = :id")
    suspend fun updateCommand(id: Long, name: String, arguments: String)

    @Query("DELETE FROM download_commands WHERE id IN (:ids)")
    suspend fun deleteCommands(ids: Collection<Long>)

    @Query("SELECT * FROM download_watches ORDER BY title COLLATE NOCASE")
    fun observeWatches(): Flow<List<DownloadWatchEntity>>

    @Query("SELECT * FROM download_watches")
    suspend fun watches(): List<DownloadWatchEntity>

    @Insert
    suspend fun insertWatch(watch: DownloadWatchEntity): Long

    @Query("UPDATE download_watches SET knownIds = :knownIds WHERE id = :id")
    suspend fun setKnownIds(id: Long, knownIds: String)

    @Query("DELETE FROM download_watches WHERE id IN (:ids)")
    suspend fun deleteWatches(ids: Collection<Long>)
}
