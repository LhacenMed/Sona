package com.lhacenmed.sona.core.vault.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * How often each moment of a Private Folder video has been watched - the library's heatmaps, kept apart with the rest of the
 * vault so nothing of it reaches the library's database or a backup. Goes with its item.
 */
@Entity(
    tableName = "vault_replay_heatmaps",
    foreignKeys = [
        ForeignKey(
            entity = VaultItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class VaultReplayHeatmap(
    @PrimaryKey val itemId: Long,
    val durationMs: Long,
    val playedMs: ByteArray,
)

@Dao
interface VaultReplayHeatmapDao {

    @Query("SELECT * FROM vault_replay_heatmaps WHERE itemId = :itemId")
    suspend fun get(itemId: Long): VaultReplayHeatmap?

    @Upsert
    suspend fun upsert(heatmap: VaultReplayHeatmap)
}
