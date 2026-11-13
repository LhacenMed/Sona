package com.lhacenmed.sona.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lhacenmed.sona.core.database.dao.AddedDateDao
import com.lhacenmed.sona.core.database.dao.FileYearDao
import com.lhacenmed.sona.core.database.dao.HeldLyricsDao
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArrangementDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.BackupDao
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.dao.FavoriteCollectionDao
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.LyricsIndexDao
import com.lhacenmed.sona.core.database.dao.MediaHashDao
import com.lhacenmed.sona.core.database.dao.OnlineHeatmapDao
import com.lhacenmed.sona.core.database.dao.PlayStatsDao
import com.lhacenmed.sona.core.database.dao.PlaylistDao
import com.lhacenmed.sona.core.database.dao.QueueItemDao
import com.lhacenmed.sona.core.database.dao.ReplayHeatmapDao
import com.lhacenmed.sona.core.database.dao.ResumePositionDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.dao.TrackMoveDao
import com.lhacenmed.sona.core.database.dao.VideoChapterDao
import com.lhacenmed.sona.core.database.dao.VideoExtrasDao
import com.lhacenmed.sona.core.database.dao.VideoSubtitleDao
import com.lhacenmed.sona.core.database.entity.AddedDateEntity
import com.lhacenmed.sona.core.database.entity.FileYearEntity
import com.lhacenmed.sona.core.database.entity.HeldLyricsEntity
import com.lhacenmed.sona.core.database.entity.AlbumEntity
import com.lhacenmed.sona.core.database.entity.ArrangementEntity
import com.lhacenmed.sona.core.database.entity.ArtistEntity
import com.lhacenmed.sona.core.database.entity.DownloadCommandEntity
import com.lhacenmed.sona.core.database.entity.DownloadCommandShortcutEntity
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.DownloadWatchEntity
import com.lhacenmed.sona.core.database.entity.FavoriteCollectionEntity
import com.lhacenmed.sona.core.database.entity.GenreEntity
import com.lhacenmed.sona.core.database.entity.LyricsIndexEntity
import com.lhacenmed.sona.core.database.entity.MediaHashEntity
import com.lhacenmed.sona.core.database.entity.OnlineHeatmapEntity
import com.lhacenmed.sona.core.database.entity.PlayStatsEntity
import com.lhacenmed.sona.core.database.entity.PlaylistEntity
import com.lhacenmed.sona.core.database.entity.PlaylistTrackEntity
import com.lhacenmed.sona.core.database.entity.QueueItemEntity
import com.lhacenmed.sona.core.database.entity.ReplayHeatmapEntity
import com.lhacenmed.sona.core.database.entity.ResumePositionEntity
import com.lhacenmed.sona.core.database.entity.TrackEntity
import com.lhacenmed.sona.core.database.entity.VideoChapterEntity
import com.lhacenmed.sona.core.database.entity.VideoExtraReadEntity
import com.lhacenmed.sona.core.database.entity.VideoSubtitleEntity

@Database(
    entities = [
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        GenreEntity::class,
        QueueItemEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlayStatsEntity::class,
        ResumePositionEntity::class,
        ArrangementEntity::class,
        FavoriteCollectionEntity::class,
        LyricsIndexEntity::class,
        MediaHashEntity::class,
        VideoSubtitleEntity::class,
        AddedDateEntity::class,
        HeldLyricsEntity::class,
        FileYearEntity::class,
        ReplayHeatmapEntity::class,
        DownloadEntity::class,
        OnlineHeatmapEntity::class,
        VideoChapterEntity::class,
        VideoExtraReadEntity::class,
        DownloadCommandEntity::class,
        DownloadCommandShortcutEntity::class,
        DownloadWatchEntity::class,
    ],
    version = 36,
    exportSchema = true,
)
@TypeConverters(CoverArtUrisConverter::class, ArtistIdsConverter::class)
abstract class SonaDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun genreDao(): GenreDao
    abstract fun queueItemDao(): QueueItemDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playStatsDao(): PlayStatsDao
    abstract fun resumePositionDao(): ResumePositionDao
    abstract fun arrangementDao(): ArrangementDao
    abstract fun favoriteCollectionDao(): FavoriteCollectionDao
    abstract fun lyricsIndexDao(): LyricsIndexDao
    abstract fun trackMoveDao(): TrackMoveDao

    abstract fun mediaHashDao(): MediaHashDao
    abstract fun backupDao(): BackupDao
    abstract fun videoSubtitleDao(): VideoSubtitleDao
    abstract fun addedDateDao(): AddedDateDao
    abstract fun heldLyricsDao(): HeldLyricsDao
    abstract fun fileYearDao(): FileYearDao
    abstract fun replayHeatmapDao(): ReplayHeatmapDao
    abstract fun downloadDao(): DownloadDao
    abstract fun onlineHeatmapDao(): OnlineHeatmapDao
    abstract fun videoChapterDao(): VideoChapterDao
    abstract fun videoExtrasDao(): VideoExtrasDao

    companion object {
        const val FILE_NAME = "sona.db"
    }
}
