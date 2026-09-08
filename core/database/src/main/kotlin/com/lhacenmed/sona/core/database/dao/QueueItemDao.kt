package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lhacenmed.sona.core.database.entity.QueueItemEntity

@Dao
interface QueueItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItemEntity>)

    @Query("SELECT * FROM queue_items ORDER BY trackOrder")
    suspend fun getAll(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrent(): QueueItemEntity?

    @Query("DELETE FROM queue_items")
    suspend fun clear()

    /**
     * Full delete-and-reinsert of the whole queue, mirroring Fossify's `AudioHelper.resetQueue` -
     * every save keeps the table trivially consistent with whatever order the player currently
     * holds, rather than diffing/patching individual rows.
     */
    @Transaction
    suspend fun resetQueue(items: List<QueueItemEntity>) {
        clear()
        insertAll(items)
    }
}
