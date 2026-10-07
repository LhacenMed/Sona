package com.lhacenmed.sona.core.vault.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The Private Folder's own list, apart from the library's database: that one is backed up, and the vault
 * is not - see `vaultDirectory`.
 */
@Database(entities = [VaultItem::class], version = 1, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun itemDao(): VaultItemDao
}
