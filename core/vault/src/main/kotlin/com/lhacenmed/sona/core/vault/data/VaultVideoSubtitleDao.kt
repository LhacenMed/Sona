package com.lhacenmed.sona.core.vault.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface VaultVideoSubtitleDao {

    @Query("SELECT * FROM vault_video_subtitles WHERE itemId = :itemId")
    suspend fun get(itemId: Long): VaultVideoSubtitle?

    @Upsert
    suspend fun upsert(subtitle: VaultVideoSubtitle)
}
