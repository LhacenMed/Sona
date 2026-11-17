package com.lhacenmed.sona.feature.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lhacenmed.sona.feature.update.notification.UpdateCheckWorker
import java.util.concurrent.TimeUnit

/**
 * The update work done while the app is closed, and the one place it is scheduled: looking for a new version
 * every [CheckIntervalHours] ([UpdateCheckWorker]), and delivering one found ([UpdateDeliveryWorker]). Each
 * has one name, so it is never queued twice, and survives the app being closed and the device restarting.
 */
object UpdateWork {
    private const val CHECK_WORK = "update_check_work"
    private const val DELIVERY_WORK = "update_delivery_work"

    private const val CheckIntervalHours = 6L
    private const val CheckFlexMinutes = 30L

    /** How long a delivery that could not finish waits before its next attempt - longer by as much each time. */
    private const val DeliveryRetryMinutes = 10L

    /** Keeps the background check scheduled while [isWanted] - updates installed by themselves, or announced. */
    fun followChecks(context: Context, isWanted: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!isWanted) {
            workManager.cancelUniqueWork(CHECK_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            CheckIntervalHours, TimeUnit.HOURS,
            CheckFlexMinutes, TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build(),
        ).build()
        workManager.enqueueUniquePeriodicWork(CHECK_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Delivers the newest update - on Wi-Fi, or any network that costs nothing, unless [usesMobileData]. One
     * already queued or under way stands, as it delivers whatever is newest when it runs; [replacing] it is
     * for a connection newly allowed or refused, which it must start again to follow.
     */
    fun deliver(context: Context, usesMobileData: Boolean, replacing: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<UpdateDeliveryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (usesMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, DeliveryRetryMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DELIVERY_WORK,
            if (replacing) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelDelivery(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DELIVERY_WORK)
    }
}
