package com.lhacenmed.sona.feature.update.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lhacenmed.sona.feature.update.R
import com.lhacenmed.sona.feature.update.UpdateService
import com.lhacenmed.sona.feature.update.github.Release
import java.util.concurrent.TimeUnit

/**
 * The update notification - ArchiveTune's `UpdateNotificationManager`: while turned on, a background check
 * every six hours on any network, and one notification per new version, which opens the Updates screen and
 * carries a Download action that starts the in-app download straight away.
 */
object UpdateNotifier {
    private const val CHANNEL_ID = "update_notification_channel"
    private const val NOTIFICATION_ID = 9999
    private const val WORK_NAME = "update_check_work"
    private const val CHECK_INTERVAL_HOURS = 6L
    private const val FLEX_MINUTES = 30L

    private const val PREFS = "update_notifier"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"

    /**
     * The extra the notification opens the app with, asking for the Updates screen. The app, which knows
     * the screen, reads it.
     */
    const val EXTRA_OPEN_UPDATES = "com.lhacenmed.sona.extra.OPEN_UPDATES"

    /** Keeps the background check scheduled while [enabled], and gone - its notification too - while not. */
    fun follow(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            CHECK_INTERVAL_HOURS, TimeUnit.HOURS,
            FLEX_MINUTES, TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build(),
        ).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Notifies of [release] - once: a version already announced is not announced again. */
    internal fun notifyIfNew(context: Context, release: Release) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_NOTIFIED_VERSION, null) == release.versionName) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        show(context, release)
        prefs.edit { putString(KEY_LAST_NOTIFIED_VERSION, release.versionName) }
    }

    private fun show(context: Context, release: Release) {
        ensureChannel(context)
        val openUpdates = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?.putExtra(EXTRA_OPEN_UPDATES, true)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_text, release.versionName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply {
                openUpdates?.let {
                    setContentIntent(PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
                if (release.apkUrl != null) {
                    addAction(
                        R.drawable.ic_update,
                        context.getString(R.string.update_notification_download),
                        PendingIntent.getForegroundService(
                            context,
                            1,
                            UpdateService.startIntent(context, release),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
            }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.update_notification_channel_description) },
        )
    }
}
