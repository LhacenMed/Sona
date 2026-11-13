package com.lhacenmed.sona.feature.download.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.downloader.YtDlp
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.feature.download.DownloadQueue
import com.lhacenmed.sona.feature.download.DownloadRunner
import com.lhacenmed.sona.feature.download.WatchChecker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

private const val DOWNLOADS = "downloads"
private const val DOWNLOADS_LATER = "downloads_later"
private const val YT_DLP_UPDATE = "yt_dlp_update"
private const val WATCH_CHECK = "download_watch_check"

/** How often the notification is redrawn while downloads run: often enough to move, rarely enough not to be throttled. */
private const val NotificationEveryMs = 1_000L

/**
 * Every download is run here, by WorkManager - in the foreground, with its notification, so it outlives the screen and the app
 * being closed - once the network the settings ask for is there. One run at a time: what is queued while it runs is picked up by
 * it; a download set to start later wakes it then.
 */
object DownloadWork {

    /** Has the queue run, as soon as the network [preferences] ask for is there. */
    fun start(context: Context, preferences: DownloaderPreferences) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(networkConstraints(preferences))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // Queued behind a run that may be ending, never dropped for it: what it no longer looks at, this one runs.
        WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOADS, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Wakes the queue at [at] - for the soonest download set to start later. */
    internal fun startAt(context: Context, at: Long, preferences: DownloaderPreferences) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(networkConstraints(preferences))
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOADS_LATER, ExistingWorkPolicy.REPLACE, request)
    }

    /** Keeps yt-dlp up to date every week, and watched channels looked at twice a day - whatever screen is open, or none. */
    fun followMaintenance(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        workManager.enqueueUniquePeriodicWork(
            YT_DLP_UPDATE,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<YtDlpUpdateWorker>(7, TimeUnit.DAYS).setConstraints(online).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            WATCH_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchCheckWorker>(12, TimeUnit.HOURS).setConstraints(online).build(),
        )
    }

    private fun networkConstraints(preferences: DownloaderPreferences) = Constraints.Builder()
        .setRequiredNetworkType(if (preferences.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresStorageNotLow(true)
        .build()
}

/** Runs the queue to its end, in the foreground - then wakes it again for the soonest download set to start later. */
internal class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo() = DownloadNotifications.foregroundInfo(applicationContext, first = null)

    @OptIn(FlowPreview::class)
    override suspend fun doWork(): Result {
        val entryPoint = entryPointOf(applicationContext)
        setForeground(getForegroundInfo())
        coroutineScope {
            val notification = launch {
                var shown = emptySet<Int>()
                var seen = emptySet<Long>()
                try {
                    entryPoint.queue().running.sample(NotificationEveryMs).collect { running ->
                        val downloads = running.values.toList()
                        // The first in the foreground service's own, every other in one of its own.
                        downloads.firstOrNull()?.let { setForeground(DownloadNotifications.foregroundInfo(applicationContext, it)) }
                        shown = DownloadNotifications.showOthers(applicationContext, downloads.drop(1), shown)
                        // Running again, a download paused from its notification no longer says it is.
                        (running.keys - seen).forEach { DownloadNotifications.cancelPaused(applicationContext, it) }
                        seen = running.keys
                    }
                } finally {
                    DownloadNotifications.showOthers(applicationContext, emptyList(), shown)
                }
            }
            val waitsForNetwork = entryPoint.runner().runQueue()
            notification.cancel()
            // Cut off by the device going offline: run again once it is back, carrying on from the last byte.
            if (waitsForNetwork) DownloadWork.start(applicationContext, entryPoint.settings().preferences.value)
        }
        entryPoint.dao().nextStartAt(System.currentTimeMillis())?.let {
            DownloadWork.startAt(applicationContext, it, entryPoint.settings().preferences.value)
        }
        return Result.success()
    }
}

/** Brings yt-dlp up to date, while the downloader is here: the sites it reads change under it within weeks. */
internal class YtDlpUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entryPoint = entryPointOf(applicationContext)
        if (entryPoint.runtime().state.value != RuntimeState.Installed) return Result.success()
        return try {
            entryPoint.ytDlp().update()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

/** Looks at every watched channel and playlist, queueing what is new on them - see [WatchChecker]. */
internal class WatchCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        entryPointOf(applicationContext).watchChecker().checkAll()
        return Result.success()
    }
}

private fun entryPointOf(context: Context): DownloadEntryPoint = EntryPointAccessors.fromApplication(context, DownloadEntryPoint::class.java)

/** The workers and the notification's actions are made by Android, not Hilt, so they reach what they need through here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DownloadEntryPoint {
    fun queue(): DownloadQueue
    fun runner(): DownloadRunner
    fun dao(): DownloadDao
    fun settings(): DownloadSettings
    fun runtime(): DownloaderRuntime
    fun ytDlp(): YtDlp
    fun watchChecker(): WatchChecker
}
