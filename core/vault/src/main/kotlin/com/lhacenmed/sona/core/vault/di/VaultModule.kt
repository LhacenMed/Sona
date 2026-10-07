package com.lhacenmed.sona.core.vault.di

import android.content.Context
import androidx.room.Room
import com.lhacenmed.sona.core.vault.data.VaultDatabase
import com.lhacenmed.sona.core.vault.data.VaultItemDao
import com.lhacenmed.sona.core.vault.vaultDirectory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VaultModule {
    @Provides
    @Singleton
    fun provideVaultDatabase(@ApplicationContext context: Context): VaultDatabase =
        Room.databaseBuilder(context, VaultDatabase::class.java, File(context.vaultDirectory, "vault.db").path).build()

    @Provides
    fun provideVaultItemDao(database: VaultDatabase): VaultItemDao = database.itemDao()
}
