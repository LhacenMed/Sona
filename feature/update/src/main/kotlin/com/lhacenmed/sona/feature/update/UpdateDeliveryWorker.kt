package com.lhacenmed.sona.feature.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lhacenmed.sona.feature.update.notification.UpdateSettingsEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Delivers the newest update by itself, start to finish - what [UpdateWork.deliver] runs, on a connection the
 * user allows: looks again for what is newest, so a run that waited days delivers today's release, then
 * [AutoUpdater] stages and installs it.
 *
 * Every step that cannot be taken yet - GitHub out of reach, a download cut short, the app in use - is come
 * back to, WorkManager waiting longer each time; one already taken is not taken again.
 */
class UpdateDeliveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = EntryPointAccessors
            .fromApplication(applicationContext, UpdateSettingsEntryPoint::class.java)
            .updateSettings()
        if (!settings.autoUpdate.value) return Result.success()

        val latest = UpdateChecker.check(applicationContext, settings.channel.value).getOrElse { return Result.retry() }
        if (!UpdateChecker.isUpdate(applicationContext, latest)) return Result.success()
        val staged = AutoUpdater.stage(applicationContext, latest) ?: return Result.retry()
        return if (AutoUpdater.install(applicationContext, staged)) Result.success() else Result.retry()
    }
}
