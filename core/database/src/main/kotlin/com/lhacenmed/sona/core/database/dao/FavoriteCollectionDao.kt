package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lhacenmed.sona.core.database.entity.FavoriteCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteCollectionDao {

    /** Every favorited collection's identity, the latest favorited first. */
    @Query("SELECT collection FROM favorite_collections ORDER BY favoritedAt DESC")
    fun observeAll(): Flow<List<String>>

    /** IGNORE, so a collection favorited again keeps the place it was first favorited at. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rows: List<FavoriteCollectionEntity>)

    @Query("DELETE FROM favorite_collections WHERE collection IN (:collections)")
    suspend fun delete(collections: List<String>)
}
