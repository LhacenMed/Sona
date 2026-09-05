package com.lhacenmed.sona.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.AlbumEntity
import com.lhacenmed.sona.core.database.entity.ArtistEntity
import com.lhacenmed.sona.core.database.entity.GenreEntity
import com.lhacenmed.sona.core.database.entity.TrackEntity

@Database(
    entities = [TrackEntity::class, AlbumEntity::class, ArtistEntity::class, GenreEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SonaDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun genreDao(): GenreDao

    companion object {
        const val FILE_NAME = "sona.db"
    }
}
