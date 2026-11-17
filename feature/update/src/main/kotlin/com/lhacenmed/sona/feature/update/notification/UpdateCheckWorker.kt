package com.lhacenmed.sona.feature.update.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lhacenmed.sona.core.datastore.UpdateSettings
import com.lhacenmed.sona.feature.update.UpdateChecker
import com.lhacenmed.sona.feature.update.UpdateWork
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The background check [UpdateWork.followChecks] schedules - ArchiveTune's `UpdateCheckWorker`: looks on the
 * chosen channel, and has a newer version delivered where the app updates itself, announced where it only
 * notifies. A failed check is retried with WorkManager's backoff.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = EntryPointAccessors
            .fromApplication(applicationContext, UpdateSettingsEntryPoint::class.java)
            .updateSettings()
        val autoUpdate = settings.autoUpdate.value
        if (!autoUpdate && !settings.notifications.value) return Result.success()

        return UpdateChecker.check(applicationContext, settings.channel.value, forceRefresh = true).fold(
            onSuccess = { latest ->
                when {
                    !UpdateChecker.isUpdate(applicationContext, latest) -> Unit
                    autoUpdate -> UpdateWork.deliver(applicationContext, settings.usesMobileData.value)
                    else -> UpdateNotifier.notifyAvailable(applicationContext, latest)
                }
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }
}

/** The workers are made by WorkManager, not Hilt, so they reach the stored settings through here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateSettingsEntryPoint {
    fun updateSettings(): UpdateSettings
}
