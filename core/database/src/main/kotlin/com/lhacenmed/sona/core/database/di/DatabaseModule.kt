package com.lhacenmed.sona.core.database.di

import android.content.Context
import androidx.room.Room
import com.lhacenmed.sona.core.database.MIGRATION_4_5
import com.lhacenmed.sona.core.database.SeedBuiltInPlaylists
import com.lhacenmed.sona.core.database.SonaDatabase
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.PlayStatsDao
import com.lhacenmed.sona.core.database.dao.PlaylistDao
import com.lhacenmed.sona.core.database.dao.QueueItemDao
import com.lhacenmed.sona.core.database.dao.TrackDao
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
            .addMigrations(MIGRATION_4_5)
            // Seeds Favourites on a fresh install; MIGRATION_4_5 does the same for an existing one.
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
}
