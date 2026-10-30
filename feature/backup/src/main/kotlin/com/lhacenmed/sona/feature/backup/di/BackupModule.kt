package com.lhacenmed.sona.feature.backup.di

import com.lhacenmed.sona.feature.backup.media.CloudMediaStore
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface BackupModule {

    /** None until a cloud is connected: a restore then lists the files a device is missing, without fetching them. */
    @BindsOptionalOf
    fun cloudMediaStore(): CloudMediaStore
}
