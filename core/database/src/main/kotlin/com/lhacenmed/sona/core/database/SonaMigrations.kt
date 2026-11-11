package com.lhacenmed.sona.core.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lhacenmed.sona.core.model.FAVORITES_PLAYLIST_ID

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
 * Lyrics, read from each track's tags or typed in by the user.
 *
 * A new table and nothing else, so existing rows are untouched. Written rather than left to the
 * destructive fallback, which would throw away the playlists and play counts earlier migrations kept.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lyrics` (
                `trackId` INTEGER NOT NULL,
                `lyrics` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`),
                FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}

/**
 * A cover each playlist can be given in its editor. Every existing playlist keeps the stacked cover it
 * already had.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `coverSource` TEXT NOT NULL DEFAULT 'STACKED'")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `coverTrackId` INTEGER")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `coverImageUri` TEXT")
    }
}

/**
 * Where each queued track plays in the shuffled order. A queue saved before this has no order to
 * give back, so its rows share one position and it is dealt again when restored.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `queue_items` ADD COLUMN `shufflePosition` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Videos join the library, their sound played as a track's is; every row stored so far is music. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `isVideo` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Lyrics are read from each file's tags as they are wanted, and written into them; none are kept here any more. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `lyrics`")
    }
}

/** Videos carry on from where they were left: one row per video left part-way through. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `resume_positions` (
                `trackId` INTEGER NOT NULL,
                `positionMs` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`),
                FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}

/**
 * Every list's hand-made order moves into one table, so albums, artists, genres and folders can be
 * arranged as playlists are. A playlist's positions become its arrangement as they stand - the order
 * it shows in its custom sort is unchanged - and its membership keeps only when each track joined.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `arrangements` (
                `list` TEXT NOT NULL,
                `instanceId` TEXT NOT NULL,
                `trackId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                PRIMARY KEY(`list`, `instanceId`, `trackId`),
                FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_arrangements_trackId` ON `arrangements` (`trackId`)")
        db.execSQL(
            "INSERT INTO `arrangements` (`list`, `instanceId`, `trackId`, `position`) " +
                "SELECT 'PLAYLIST_TRACKS', CAST(`playlistId` AS TEXT), `trackId`, `position` FROM `playlist_tracks`",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlist_tracks_new` (
                `playlistId` INTEGER NOT NULL,
                `trackId` INTEGER NOT NULL,
                `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`playlistId`, `trackId`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO `playlist_tracks_new` (`playlistId`, `trackId`, `addedAt`) " +
                "SELECT `playlistId`, `trackId`, `addedAt` FROM `playlist_tracks`",
        )
        db.execSQL("DROP TABLE `playlist_tracks`")
        db.execSQL("ALTER TABLE `playlist_tracks_new` RENAME TO `playlist_tracks`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` ON `playlist_tracks` (`trackId`)")
    }
}

/** Albums, artists, genres, folders and playlists can be favorited as themselves, for Favorites to list. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorite_collections` " +
                "(`collection` TEXT NOT NULL, `favoritedAt` INTEGER NOT NULL, PRIMARY KEY(`collection`))",
        )
    }
}

/**
 * What search reads each track's lyrics from - see `LyricsIndexEntity`. A new table and nothing else, so
 * every row kept so far - the playlists, play counts, queue and favorites - is untouched; it starts empty
 * and fills in the background from the files, which still alone hold the lyrics.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `lyrics_index` USING FTS4(`lyrics` TEXT NOT NULL, " +
                "`searchText` TEXT NOT NULL, `signature` INTEGER NOT NULL, tokenize=unicode61, " +
                "notindexed=`lyrics`, notindexed=`signature`)",
        )
    }
}

/**
 * Every track and album can be credited to more than one artist. Each keeps the one artist it had, so every
 * row - and the artists, playlists, play counts and favorites they lead to - reads on exactly as before; the
 * next scan separates the credits that name several.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `artistIds` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `tracks` SET `artistIds` = ',' || `artistId` || ','")
        db.execSQL("ALTER TABLE `albums` ADD COLUMN `artistIds` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `albums` SET `artistIds` = ',' || `artistId` || ','")
    }
}

/**
 * What a video row tells of its file - its frame and its size. Every row reads on as before, these unknown,
 * until the next scan fills them in.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `width` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `height` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `sizeBytes` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * The fingerprints backups compare files by. A table of its own, starting empty and filled as files are first
 * read; every row kept so far is untouched.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_hashes` (`path` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                "`modifiedAt` INTEGER NOT NULL, `fingerprint` TEXT NOT NULL, PRIMARY KEY(`path`))",
        )
    }
}

/**
 * What the user settles on for each video's subtitles. A table of its own, starting empty; every row kept so far is
 * untouched.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `video_subtitles` (`trackId` INTEGER NOT NULL, `attachedName` TEXT, " +
                "`offsetMs` INTEGER NOT NULL, `choice` TEXT, PRIMARY KEY(`trackId`))",
        )
    }
}

/**
 * When restored files count as added - see `AddedDateEntity`. A table of its own, starting empty; every row kept so far
 * is untouched.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `added_dates` (`path` TEXT NOT NULL, `addedAtSeconds` INTEGER NOT NULL, PRIMARY KEY(`path`))")
    }
}

/**
 * The files seen holding lyrics - see `HeldLyricsEntity`. A table of its own, every row kept so far untouched; started
 * with every file the lyrics index has already read lyrics from, so none of them is searched for again once emptied.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `held_lyrics` (`path` TEXT NOT NULL, PRIMARY KEY(`path`))")
        db.execSQL(
            "INSERT OR IGNORE INTO `held_lyrics` (`path`) " +
                "SELECT `tracks`.`path` FROM `lyrics_index` JOIN `tracks` ON `tracks`.`id` = `lyrics_index`.`rowid` " +
                "WHERE `lyrics_index`.`lyrics` != ''",
        )
    }
}

/** The years read from files MediaStore gives none - see `FileYearEntity`. A table of its own, starting empty. */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `file_years` (`path` TEXT NOT NULL, `signature` INTEGER NOT NULL, `year` INTEGER, PRIMARY KEY(`path`))")
    }
}

/** How often each moment of each video has been watched - see `ReplayHeatmapEntity`. A table of its own, starting empty. */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `replay_heatmaps` (`trackId` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                "`playedMs` BLOB NOT NULL, PRIMARY KEY(`trackId`), " +
                "FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/** The downloads asked for, and the Most replayed graphs downloaded videos came with - two tables of their own, empty. */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, " +
                "`title` TEXT, `kind` TEXT NOT NULL, `videoQuality` TEXT NOT NULL, `audioFormat` TEXT NOT NULL, " +
                "`captions` INTEGER NOT NULL, `skipSponsors` INTEGER NOT NULL, `status` TEXT NOT NULL, `error` TEXT, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS `online_heatmaps` (`path` TEXT NOT NULL, `heights` BLOB NOT NULL, PRIMARY KEY(`path`))")
    }
}

/**
 * The sites' Most replayed graphs kept by track rather than by file, so each goes where its track goes, and with it: the
 * table made anew, each graph kept for the track whose file it was downloaded to - one whose file the library has not read
 * in has no track to go with, and goes.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `online_heatmaps_by_track` (`trackId` INTEGER NOT NULL, `heights` BLOB NOT NULL, " +
                "PRIMARY KEY(`trackId`), FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT OR REPLACE INTO `online_heatmaps_by_track` (`trackId`, `heights`) " +
                "SELECT `tracks`.`id`, `online_heatmaps`.`heights` FROM `online_heatmaps` JOIN `tracks` ON `tracks`.`path` = `online_heatmaps`.`path`",
        )
        db.execSQL("DROP TABLE `online_heatmaps`")
        db.execSQL("ALTER TABLE `online_heatmaps_by_track` RENAME TO `online_heatmaps`")
    }
}

/** Downloaded videos' chapters, by track - see `VideoChapterEntity`. A table of its own, starting empty. */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `video_chapters` (`trackId` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                "PRIMARY KEY(`trackId`, `startMs`), FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/** Which videos' files have been read for what they carry of their sites - see `VideoExtraReadEntity`. Empty: each is read once. */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `video_extra_reads` (`trackId` INTEGER NOT NULL, `signature` INTEGER NOT NULL, " +
                "PRIMARY KEY(`trackId`), FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/**
 * What a download keeps beyond its choices - the one-off options it was asked with, why it failed, what it was found to be and
 * what it saved, for the history - and the downloader's saved commands and watched channels, each a table starting empty.
 * Every column is added: what is queued or done keeps all it had.
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "`formatId` TEXT",
            "`clipStart` INTEGER",
            "`clipEnd` INTEGER",
            "`splitChapters` INTEGER NOT NULL DEFAULT 0",
            "`commandArguments` TEXT",
            "`failure` TEXT",
            "`thumbnailUrl` TEXT",
            "`uploader` TEXT",
            "`site` TEXT",
            "`durationSeconds` INTEGER",
            "`savedPaths` TEXT NOT NULL DEFAULT ''",
            "`sizeBytes` INTEGER NOT NULL DEFAULT 0",
            "`finishedAt` INTEGER",
            "`startAt` INTEGER",
        ).forEach { db.execSQL("ALTER TABLE `downloads` ADD COLUMN $it") }
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `download_commands` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `arguments` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `download_watches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `knownIds` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
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

/** The video format a download was asked in - Quality for every one queued or done before, as each was downloaded. */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `videoFormat` TEXT NOT NULL DEFAULT 'QUALITY'")
    }
}

/** Each download's sound language and captions, as picked for it. */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `audioLanguage` TEXT")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `subtitlesAsFile` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `subtitleLanguages` TEXT")
    }
}

/** Whether each download's sound is saved as a song, and the song picked for it. */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `savesAsSong` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `song` TEXT")
    }
}

/**
 * Whether each download's sound is saved with a cover; when and for which links each saved command is given by itself, and
 * whether it is picked to begin with; and the snippets commands are written with.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `embedsCover` INTEGER NOT NULL DEFAULT 1")
        listOf("addsToAudio", "addsToVideo", "addsToReading", "isPreferred").forEach { column ->
            db.execSQL("ALTER TABLE `download_commands` ADD COLUMN `$column` INTEGER NOT NULL DEFAULT 0")
        }
        db.execSQL("ALTER TABLE `download_commands` ADD COLUMN `urlPatterns` TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS `download_command_shortcuts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT NOT NULL)")
    }
}

/**
 * Whether each download's cover is kept as a picture beside it too - a sound's is always in its file now, so the column before
 * it, `embedsCover`, is no longer read.
 */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `savesCover` INTEGER NOT NULL DEFAULT 0")
    }
}
