package com.lhacenmed.sona.feature.update.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lhacenmed.sona.core.datastore.UpdateSettings
import com.lhacenmed.sona.feature.update.UpdateChecker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The background check [UpdateNotifier] schedules - ArchiveTune's `UpdateCheckWorker`: looks on the chosen
 * channel, and notifies of a newer version. A failed check is retried with WorkManager's backoff.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = EntryPointAccessors
            .fromApplication(applicationContext, UpdateSettingsEntryPoint::class.java)
            .updateSettings()
        if (!settings.notifications.value) return Result.success()

        return UpdateChecker.check(applicationContext, settings.channel.value, forceRefresh = true).fold(
            onSuccess = { latest ->
                if (UpdateChecker.isNewer(applicationContext, latest)) UpdateNotifier.notifyIfNew(applicationContext, latest)
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }
}

/** The worker is made by WorkManager, not Hilt, so it reaches the stored settings through here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateSettingsEntryPoint {
    fun updateSettings(): UpdateSettings
}
