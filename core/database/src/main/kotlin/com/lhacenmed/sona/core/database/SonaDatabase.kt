package com.lhacenmed.sona.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArrangementDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.FavoriteCollectionDao
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.PlayStatsDao
import com.lhacenmed.sona.core.database.dao.PlaylistDao
import com.lhacenmed.sona.core.database.dao.QueueItemDao
import com.lhacenmed.sona.core.database.dao.ResumePositionDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.AlbumEntity
import com.lhacenmed.sona.core.database.entity.ArrangementEntity
import com.lhacenmed.sona.core.database.entity.ArtistEntity
import com.lhacenmed.sona.core.database.entity.FavoriteCollectionEntity
import com.lhacenmed.sona.core.database.entity.GenreEntity
import com.lhacenmed.sona.core.database.entity.PlayStatsEntity
import com.lhacenmed.sona.core.database.entity.PlaylistEntity
import com.lhacenmed.sona.core.database.entity.PlaylistTrackEntity
import com.lhacenmed.sona.core.database.entity.QueueItemEntity
import com.lhacenmed.sona.core.database.entity.ResumePositionEntity
import com.lhacenmed.sona.core.database.entity.TrackEntity

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
    ],
    version = 16,
    exportSchema = true,
)
@TypeConverters(CoverArtUrisConverter::class)
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

    companion object {
        const val FILE_NAME = "sona.db"
    }
}
