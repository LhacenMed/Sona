package com.lhacenmed.sona.core.vault.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Where a Private Folder video was left part-way through - the library's resume positions, kept apart with
 * the rest of the vault so nothing of it reaches the library's database or a backup. Goes with its item.
 */
@Entity(
    tableName = "vault_resume_positions",
    foreignKeys = [
        ForeignKey(
            entity = VaultItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VaultResumePosition(
    @PrimaryKey val itemId: Long,
    val positionMs: Long,
)
