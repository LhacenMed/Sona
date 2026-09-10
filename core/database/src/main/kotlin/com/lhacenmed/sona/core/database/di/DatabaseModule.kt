package com.lhacenmed.sona.core.database.di

import android.content.Context
import androidx.room.Room
import com.lhacenmed.sona.core.database.SonaDatabase
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.GenreDao
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
            // Pre-release app, no user data to preserve yet - real migrations start once shipped.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideTrackDao(database: SonaDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideAlbumDao(database: SonaDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideArtistDao(database: SonaDatabase): ArtistDao = database.artistDao()

    @Provides
    fun provideGenreDao(database: SonaDatabase): GenreDao = database.genreDao()

    @Provides
    fun provideQueueItemDao(database: SonaDatabase): QueueItemDao = database.queueItemDao()
}
