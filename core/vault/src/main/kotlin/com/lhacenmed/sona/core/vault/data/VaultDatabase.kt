package com.lhacenmed.sona.core.vault.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The Private Folder's own list, apart from the library's database: that one is backed up, and the vault
 * is not - see `vaultDirectory`.
 */
@Database(entities = [VaultItem::class, VaultResumePosition::class, VaultVideoSubtitle::class], version = 4, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun itemDao(): VaultItemDao
    abstract fun resumePositionDao(): VaultResumePositionDao
    abstract fun videoSubtitleDao(): VaultVideoSubtitleDao
}

/** How many times each item was listened to through - none yet for every item already there. */
internal val VAULT_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `playCount` INTEGER NOT NULL DEFAULT 0")
    }
}

/** What the user settles on for each video's subtitles. A table of its own, starting empty. */
internal val VAULT_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vault_video_subtitles` (`itemId` INTEGER NOT NULL, `attachedName` TEXT, " +
                "`offsetMs` INTEGER NOT NULL, `choice` TEXT, PRIMARY KEY(`itemId`), " +
                "FOREIGN KEY(`itemId`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/**
 * A video's frame, for its row, and where each video was left. Every item is kept as it was, its frame
 * unknown; nothing has been left part-way yet.
 */
internal val VAULT_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `width` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `height` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vault_resume_positions` (`itemId` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`itemId`), FOREIGN KEY(`itemId`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}
