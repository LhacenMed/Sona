package com.lhacenmed.sona.feature.download.work

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.lhacenmed.sona.core.common.notification.SonaNotificationChannel
import com.lhacenmed.sona.core.common.notification.SonaNotificationId
import com.lhacenmed.sona.core.common.notification.SonaNotifications
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.RunningDownload
import dagger.hilt.android.EntryPointAccessors

private const val ACTION_CANCEL_ALL = "com.lhacenmed.sona.download.action.CANCEL_ALL"

/** What a download says in the shade: how far those running are while they run, then whether each was saved. */
internal object DownloadNotifications {

    /** The running downloads' notification, as the worker's foreground service shows it - the first, and how many more. */
    fun foregroundInfo(context: Context, running: Map<Long, RunningDownload>): ForegroundInfo {
        val first = running.values.firstOrNull()
        val percent = first?.fraction?.let { (it * 100).toInt() }
        val others = running.size - 1
        val text = listOfNotNull(
            percent?.let { context.getString(R.string.download_percent, it) },
            first?.bytesPerSecond?.let { context.getString(R.string.download_speed, Formatter.formatShortFileSize(context, it)) },
            if (others > 0) context.resources.getQuantityString(R.plurals.download_more_running, others, others) else null,
        ).joinToString(" · ")
        val notification = SonaNotifications.builder(context, SonaNotificationChannel.DOWNLOADS)
            .setContentTitle(first?.title ?: context.getString(R.string.download_starting))
            .setContentText(text.ifEmpty { null })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent ?: 0, first != null && percent == null)
            .addAction(0, context.getString(R.string.download_cancel), cancelAllIntent(context))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(SonaNotificationId.DOWNLOAD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(SonaNotificationId.DOWNLOAD, notification)
        }
    }

    /** Says [title] was saved - or could not be downloaded. */
    fun postResult(context: Context, title: String, isSaved: Boolean) {
        val notification = SonaNotifications.builder(context, SonaNotificationChannel.DOWNLOADS)
            .setContentTitle(title)
            .setContentText(context.getString(if (isSaved) R.string.download_saved else R.string.download_failed))
            .setAutoCancel(true)
            .build()
        SonaNotifications.post(context, SonaNotificationId.DOWNLOAD_RESULT, notification)
    }

    private fun cancelAllIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, DownloadActionReceiver::class.java).setAction(ACTION_CANCEL_ALL),
        PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Cancel, from the downloads' notification: every download running stops. */
internal class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL_ALL) return
        val queue = EntryPointAccessors.fromApplication(context, DownloadEntryPoint::class.java).queue()
        queue.cancel(queue.running.value.keys)
    }
}
