package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.FileYearEntity

@Dao
interface FileYearDao {

    @Query("SELECT * FROM file_years")
    suspend fun getAll(): List<FileYearEntity>

    @Upsert
    suspend fun upsertAll(years: List<FileYearEntity>)

    @Query("DELETE FROM file_years WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)
}
