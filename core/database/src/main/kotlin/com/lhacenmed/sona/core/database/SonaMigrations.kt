package com.lhacenmed.sona.core.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The id Favorites is seeded with. Fixed, so the heart always knows where to write. */
const val FAVORITES_PLAYLIST_ID = 1L

private const val FAVORITES_PLAYLIST_NAME = "Favorites"

/**
 * Playlists, playlist membership and play statistics.
 *
 * Written rather than left to a destructive fallback because this is the release where the database
 * starts holding things a rescan cannot rebuild. Tracks, albums and artists can always be found on
 * disk again; which songs someone chose, and in what order they arranged them, cannot.
 *
 * Favorites moves from a column on `tracks` to a playlist of its own, so the migration carries the
 * existing favorites across as its first members. `id` is ordered by title only so that the
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
 * When each playlist last changed, and when each of its tracks was added - for sorting by either.
 *
 * Neither was ever recorded, so existing rows are given the one time that is known: the playlist's
 * creation. Every playlist reads as unchanged since it was made, and its tracks as added with it.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `modifiedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `playlists` SET `modifiedAt` = `createdAt`")

        db.execSQL("ALTER TABLE `playlist_tracks` ADD COLUMN `addedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE `playlist_tracks` SET `addedAt` =
                (SELECT `createdAt` FROM `playlists` WHERE `playlists`.`id` = `playlist_tracks`.`playlistId`)
            """.trimIndent(),
        )
    }
}

/**
 * Renames the built-in playlist from "Favourites" to "Favorites", the spelling the rest of the app uses.
 *
 * OR IGNORE because playlist names are unique: an install where the user already made a playlist
 * called "Favorites" keeps the old spelling on the built-in one, rather than the upgrade failing or a
 * playlist the user named themselves being renamed for them.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE OR IGNORE `playlists` SET `name` = '$FAVORITES_PLAYLIST_NAME' " +
                "WHERE `id` = $FAVORITES_PLAYLIST_ID AND `isBuiltIn` = 1",
        )
    }
}

/**
 * Artists and genres keep every cover among their tracks rather than one, and genres how many artists
 * they span - what Auxio composes their covers from and shows beneath their names.
 *
 * Both tables are only ever what a scan found, so they are rebuilt rather than altered: SQLite cannot
 * drop the old single-cover column on every version this app supports. A row keeps the cover it had
 * until the rescan the scanner's schema version forces fills in the rest, and a genre's artist count
 * is already known from its tracks.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `artists_new` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`trackCount` INTEGER NOT NULL, `albumCount` INTEGER NOT NULL, " +
                "`coverArtUris` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT INTO `artists_new` (`id`, `name`, `trackCount`, `albumCount`, `coverArtUris`) " +
                "SELECT `id`, `name`, `trackCount`, `albumCount`, IFNULL(`coverArtUri`, '') FROM `artists`",
        )
        db.execSQL("DROP TABLE `artists`")
        db.execSQL("ALTER TABLE `artists_new` RENAME TO `artists`")

        db.execSQL(
            "CREATE TABLE `genres_new` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`trackCount` INTEGER NOT NULL, `artistCount` INTEGER NOT NULL, " +
                "`coverArtUris` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            """
            INSERT INTO `genres_new` (`id`, `name`, `trackCount`, `artistCount`, `coverArtUris`)
            SELECT `id`, `name`, `trackCount`,
                (SELECT COUNT(DISTINCT `artistId`) FROM `tracks` WHERE `tracks`.`genreId` = `genres`.`id`),
                IFNULL(`coverArtUri`, '')
            FROM `genres`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `genres`")
        db.execSQL("ALTER TABLE `genres_new` RENAME TO `genres`")
    }
}

/**
 * Makes sure Favorites exists, every time the database is opened.
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
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT OR IGNORE INTO `playlists` (`id`, `name`, `isBuiltIn`, `createdAt`, `modifiedAt`) " +
                "VALUES ($FAVORITES_PLAYLIST_ID, '$FAVORITES_PLAYLIST_NAME', 1, $now, $now)",
        )
    }
}
