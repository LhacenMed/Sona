package com.lhacenmed.sona.core.vault.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Query
import androidx.room.Upsert

/** A place in a Private Folder video the user marked - the library's bookmarks, kept apart with the rest of the vault. Goes with its item. */
@Entity(
    tableName = "vault_video_bookmarks",
    primaryKeys = ["itemId", "positionMs"],
    foreignKeys = [
        ForeignKey(
            entity = VaultItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class VaultVideoBookmark(
    val itemId: Long,
    val positionMs: Long,
)

@Dao
interface VaultVideoBookmarkDao {

    @Query("SELECT positionMs FROM vault_video_bookmarks WHERE itemId = :itemId ORDER BY positionMs")
    suspend fun get(itemId: Long): List<Long>

    @Upsert
    suspend fun upsert(bookmark: VaultVideoBookmark)

    @Query("DELETE FROM vault_video_bookmarks WHERE itemId = :itemId AND positionMs = :positionMs")
    suspend fun delete(itemId: Long, positionMs: Long)
}
