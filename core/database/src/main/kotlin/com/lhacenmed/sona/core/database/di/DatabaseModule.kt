package com.lhacenmed.sona.core.database.di

import android.content.Context
import androidx.room.Room
import com.lhacenmed.sona.core.database.MIGRATION_4_5
import com.lhacenmed.sona.core.database.MIGRATION_5_6
import com.lhacenmed.sona.core.database.MIGRATION_6_7
import com.lhacenmed.sona.core.database.MIGRATION_7_8
import com.lhacenmed.sona.core.database.MIGRATION_8_9
import com.lhacenmed.sona.core.database.MIGRATION_9_10
import com.lhacenmed.sona.core.database.MIGRATION_10_11
import com.lhacenmed.sona.core.database.MIGRATION_11_12
import com.lhacenmed.sona.core.database.MIGRATION_12_13
import com.lhacenmed.sona.core.database.MIGRATION_13_14
import com.lhacenmed.sona.core.database.MIGRATION_14_15
import com.lhacenmed.sona.core.database.MIGRATION_15_16
import com.lhacenmed.sona.core.database.MIGRATION_16_17
import com.lhacenmed.sona.core.database.MIGRATION_17_18
import com.lhacenmed.sona.core.database.MIGRATION_18_19
import com.lhacenmed.sona.core.database.MIGRATION_19_20
import com.lhacenmed.sona.core.database.MIGRATION_20_21
import com.lhacenmed.sona.core.database.MIGRATION_21_22
import com.lhacenmed.sona.core.database.MIGRATION_22_23
import com.lhacenmed.sona.core.database.MIGRATION_23_24
import com.lhacenmed.sona.core.database.MIGRATION_24_25
import com.lhacenmed.sona.core.database.MIGRATION_25_26
import com.lhacenmed.sona.core.database.MIGRATION_26_27
import com.lhacenmed.sona.core.database.MIGRATION_27_28
import com.lhacenmed.sona.core.database.MIGRATION_28_29
import com.lhacenmed.sona.core.database.SeedBuiltInPlaylists
import com.lhacenmed.sona.core.database.SonaDatabase
import com.lhacenmed.sona.core.database.dao.AddedDateDao
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.dao.FileYearDao
import com.lhacenmed.sona.core.database.dao.OnlineHeatmapDao
import com.lhacenmed.sona.core.database.dao.VideoChapterDao
import com.lhacenmed.sona.core.database.dao.VideoExtrasDao
import com.lhacenmed.sona.core.database.dao.ReplayHeatmapDao
import com.lhacenmed.sona.core.database.dao.HeldLyricsDao
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArrangementDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.BackupDao
import com.lhacenmed.sona.core.database.dao.FavoriteCollectionDao
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.LyricsIndexDao
import com.lhacenmed.sona.core.database.dao.MediaHashDao
import com.lhacenmed.sona.core.database.dao.PlayStatsDao
import com.lhacenmed.sona.core.database.dao.PlaylistDao
import com.lhacenmed.sona.core.database.dao.QueueItemDao
import com.lhacenmed.sona.core.database.dao.ResumePositionDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.dao.TrackMoveDao
import com.lhacenmed.sona.core.database.dao.VideoSubtitleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideSonaDatabase(@ApplicationContext context: Context): SonaDatabase =
        Room.databaseBuilder(context, SonaDatabase::class.java, SonaDatabase.FILE_NAME)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29)
            // Seeds Favorites on a fresh install; MIGRATION_4_5 does the same for an existing one.
            .addCallback(SeedBuiltInPlaylists)
            // Still a net for a version pair no migration covers. Real migrations take precedence
            // when they exist, so from v4 onward playlists and play counts survive an upgrade.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideTrackDao(database: SonaDatabase): TrackDao = database.trackDao()

    @Provides
    fun providePlaylistDao(database: SonaDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun providePlayStatsDao(database: SonaDatabase): PlayStatsDao = database.playStatsDao()

    @Provides
    fun provideAlbumDao(database: SonaDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideArtistDao(database: SonaDatabase): ArtistDao = database.artistDao()

    @Provides
    fun provideGenreDao(database: SonaDatabase): GenreDao = database.genreDao()

    @Provides
    fun provideQueueItemDao(database: SonaDatabase): QueueItemDao = database.queueItemDao()

    @Provides
    fun provideResumePositionDao(database: SonaDatabase): ResumePositionDao = database.resumePositionDao()

    @Provides
    fun provideArrangementDao(database: SonaDatabase): ArrangementDao = database.arrangementDao()

    @Provides
    fun provideFavoriteCollectionDao(database: SonaDatabase): FavoriteCollectionDao = database.favoriteCollectionDao()

    @Provides
    fun provideLyricsIndexDao(database: SonaDatabase): LyricsIndexDao = database.lyricsIndexDao()

    @Provides
    fun provideTrackMoveDao(database: SonaDatabase): TrackMoveDao = database.trackMoveDao()

    @Provides
    fun provideMediaHashDao(database: SonaDatabase): MediaHashDao = database.mediaHashDao()

    @Provides
    fun provideBackupDao(database: SonaDatabase): BackupDao = database.backupDao()

    @Provides
    fun provideVideoSubtitleDao(database: SonaDatabase): VideoSubtitleDao = database.videoSubtitleDao()

    @Provides
    fun provideAddedDateDao(database: SonaDatabase): AddedDateDao = database.addedDateDao()

    @Provides
    fun provideHeldLyricsDao(database: SonaDatabase): HeldLyricsDao = database.heldLyricsDao()

    @Provides
    fun provideFileYearDao(database: SonaDatabase): FileYearDao = database.fileYearDao()

    @Provides
    fun provideReplayHeatmapDao(database: SonaDatabase): ReplayHeatmapDao = database.replayHeatmapDao()

    @Provides
    fun provideDownloadDao(database: SonaDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideOnlineHeatmapDao(database: SonaDatabase): OnlineHeatmapDao = database.onlineHeatmapDao()

    @Provides
    fun provideVideoChapterDao(database: SonaDatabase): VideoChapterDao = database.videoChapterDao()

    @Provides
    fun provideVideoExtrasDao(database: SonaDatabase): VideoExtrasDao = database.videoExtrasDao()
}
