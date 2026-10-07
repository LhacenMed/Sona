package com.lhacenmed.sona.core.vault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A track or video moved into the Private Folder: a plain file under the vault's own directory, named
 * [fileName], with what its row shows kept beside it - it is no longer in the library, so nothing else
 * knows its title.
 */
@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    /** What the file is called again once moved back out. */
    val originalFileName: String,
    val isVideo: Boolean,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val addedAt: Long,
)
