package com.lhacenmed.sona.core.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The id Favourites is seeded with. Fixed, so the heart always knows where to write. */
const val FAVORITES_PLAYLIST_ID = 1L

private const val FAVORITES_PLAYLIST_NAME = "Favourites"

/**
 * Playlists, playlist membership and play statistics.
 *
 * Written rather than left to a destructive fallback because this is the release where the database
 * starts holding things a rescan cannot rebuild. Tracks, albums and artists can always be found on
 * disk again; which songs someone chose, and in what order they arranged them, cannot.
 *
 * Favourites moves from a column on `tracks` to a playlist of its own, so the migration carries the
 * existing favourites across as its first members. `id` is ordered by title only so that the
 * migrated list starts in a defined order rather than in whatever order SQLite returned rows.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlists` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `isBuiltIn` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_name` ON `playlists` (`name`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                `playlistId` INTEGER NOT NULL,
                `trackId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                PRIMARY KEY(`playlistId`, `trackId`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId_position` " +
                "ON `playlist_tracks` (`playlistId`, `position`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` ON `playlist_tracks` (`trackId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `play_stats` (
                `trackId` INTEGER NOT NULL,
                `playCount` INTEGER NOT NULL,
                `lastPlayedAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`),
                FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        db.execSQL(
            "INSERT OR IGNORE INTO `playlists` (`id`, `name`, `isBuiltIn`, `createdAt`) " +
                "VALUES ($FAVORITES_PLAYLIST_ID, '$FAVORITES_PLAYLIST_NAME', 1, ${System.currentTimeMillis()})",
        )

        db.execSQL(
            """
            INSERT OR IGNORE INTO `playlist_tracks` (`playlistId`, `trackId`, `position`)
            SELECT $FAVORITES_PLAYLIST_ID, `id`, ROW_NUMBER() OVER (ORDER BY `title` COLLATE NOCASE) - 1
            FROM `tracks` WHERE `isFavorite` = 1
            """.trimIndent(),
        )

        // Rebuilt without isFavorite; SQLite cannot drop a column before 3.35, and recreating the
        // table is what Room's own generated migrations do here anyway.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tracks_new` (
                `id` INTEGER NOT NULL, `mediaStoreId` INTEGER NOT NULL, `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL, `artistId` INTEGER NOT NULL, `album` TEXT NOT NULL,
                `albumId` INTEGER NOT NULL, `genre` TEXT, `genreId` INTEGER, `path` TEXT NOT NULL,
                `folderPath` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `trackNumber` INTEGER,
                `discNumber` INTEGER, `year` INTEGER, `dateAddedSeconds` INTEGER NOT NULL,
                `coverArtUri` TEXT, `isManuallyScanned` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `tracks_new`
            SELECT `id`, `mediaStoreId`, `title`, `artist`, `artistId`, `album`, `albumId`, `genre`,
                   `genreId`, `path`, `folderPath`, `durationMs`, `trackNumber`, `discNumber`,
                   `year`, `dateAddedSeconds`, `coverArtUri`, `isManuallyScanned`
            FROM `tracks`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `tracks`")
        db.execSQL("ALTER TABLE `tracks_new` RENAME TO `tracks`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_path` ON `tracks` (`path`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_albumId` ON `tracks` (`albumId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_artistId` ON `tracks` (`artistId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_genreId` ON `tracks` (`genreId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_folderPath` ON `tracks` (`folderPath`)")
    }
}

/**
 * Makes sure Favourites exists, every time the database is opened.
 *
 * On open rather than on create, because creation is only one of the ways this database comes to
 * exist - a migration and a destructive rebuild are others, and `onCreate` fires for none of them.
 * Favouriting a track inserts a row pointing at this playlist, so if it were ever missing the
 * foreign key would fail and keep failing, with nothing to repair it. Seeding here is idempotent
 * and costs one statement per launch, and it heals a database that somehow arrived without it.
 *
 * Seeding at [FAVORITES_PLAYLIST_ID] before any user playlist can exist is what lets the id stay
 * fixed without ever colliding with an auto-generated one.
 */
object SeedBuiltInPlaylists : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO `playlists` (`id`, `name`, `isBuiltIn`, `createdAt`) " +
                "VALUES ($FAVORITES_PLAYLIST_ID, '$FAVORITES_PLAYLIST_NAME', 1, ${System.currentTimeMillis()})",
        )
    }
}
