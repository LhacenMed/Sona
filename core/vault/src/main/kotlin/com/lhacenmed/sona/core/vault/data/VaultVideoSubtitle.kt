package com.lhacenmed.sona.core.vault.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * What the user settled on for a Private Folder video's subtitles - the library's own, kept apart with the rest of
 * the vault so nothing of it reaches the library's database or a backup. Goes with its item.
 */
@Entity(
    tableName = "vault_video_subtitles",
    foreignKeys = [
        ForeignKey(
            entity = VaultItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VaultVideoSubtitle(
    @PrimaryKey val itemId: Long,
    val attachedName: String?,
    val offsetMs: Long,
    val choice: String?,
)
