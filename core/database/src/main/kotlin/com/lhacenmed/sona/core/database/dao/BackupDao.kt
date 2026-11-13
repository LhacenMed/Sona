package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.ArrangementEntity
import com.lhacenmed.sona.core.database.entity.DownloadCommandEntity
import com.lhacenmed.sona.core.database.entity.DownloadCommandShortcutEntity
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.DownloadWatchEntity
import com.lhacenmed.sona.core.database.entity.FavoriteCollectionEntity
import com.lhacenmed.sona.core.database.entity.PlayStatsEntity
import com.lhacenmed.sona.core.database.entity.PlaylistEntity
import com.lhacenmed.sona.core.database.entity.PlaylistTrackEntity
import com.lhacenmed.sona.core.database.entity.ReplayHeatmapEntity
import com.lhacenmed.sona.core.database.entity.ResumePositionEntity
import com.lhacenmed.sona.core.database.entity.VideoSubtitleEntity

/** A library track's file. */
data class TrackFile(
    val id: Long,
    val path: String,
    val folderPath: String,
    val isVideo: Boolean,
    val durationMs: Long,
    val dateAddedSeconds: Long,
)

/**
 * Everything a backup reads of what the user made, and how a restore writes it back.
 *
 * A restore only ever adds: rows that are already there are kept, so restoring the same backup again
 * changes nothing. What wins where both have a row is decided before these are called.
 */
@Dao
interface BackupDao {

    @Query("SELECT id, path, folderPath, isVideo, durationMs, dateAddedSeconds FROM tracks")
    suspend fun trackFiles(): List<TrackFile>

    @Query("SELECT * FROM playlists")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_tracks")
    suspend fun playlistTracks(): List<PlaylistTrackEntity>

    @Query("SELECT * FROM favorite_collections")
    suspend fun favoriteCollections(): List<FavoriteCollectionEntity>

    @Query("SELECT * FROM arrangements")
    suspend fun arrangements(): List<ArrangementEntity>

    @Query("SELECT * FROM play_stats")
    suspend fun playStats(): List<PlayStatsEntity>

    @Query("SELECT * FROM resume_positions")
    suspend fun resumePositions(): List<ResumePositionEntity>

    @Query("SELECT * FROM video_subtitles")
    suspend fun videoSubtitles(): List<VideoSubtitleEntity>

    @Query("SELECT * FROM replay_heatmaps")
    suspend fun replayHeatmaps(): List<ReplayHeatmapEntity>

    @Query("SELECT * FROM downloads")
    suspend fun downloads(): List<DownloadEntity>

    @Query("SELECT * FROM download_commands")
    suspend fun downloadCommands(): List<DownloadCommandEntity>

    @Query("SELECT * FROM download_command_shortcuts")
    suspend fun downloadCommandShortcuts(): List<DownloadCommandShortcutEntity>

    @Query("SELECT * FROM download_watches")
    suspend fun downloadWatches(): List<DownloadWatchEntity>

    @Insert
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTracks(rows: List<PlaylistTrackEntity>)

    /** Moves the playlist's last change up to [modifiedAt], never back. */
    @Query("UPDATE playlists SET modifiedAt = MAX(modifiedAt, :modifiedAt) WHERE id = :playlistId")
    suspend fun raiseModifiedAt(playlistId: Long, modifiedAt: Long)

    @Query("UPDATE playlists SET coverImageUri = :imageUri WHERE id = :playlistId")
    suspend fun setCoverImage(playlistId: Long, imageUri: String?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavoriteCollections(rows: List<FavoriteCollectionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArrangements(rows: List<ArrangementEntity>)

    @Upsert
    suspend fun upsertPlayStats(rows: List<PlayStatsEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResumePositions(rows: List<ResumePositionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideoSubtitles(rows: List<VideoSubtitleEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReplayHeatmaps(rows: List<ReplayHeatmapEntity>)

    @Insert
    suspend fun insertDownloads(rows: List<DownloadEntity>)

    @Insert
    suspend fun insertDownloadCommands(rows: List<DownloadCommandEntity>)

    @Insert
    suspend fun insertDownloadCommandShortcuts(rows: List<DownloadCommandShortcutEntity>)

    @Insert
    suspend fun insertDownloadWatches(rows: List<DownloadWatchEntity>)
}
