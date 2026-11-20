package com.lhacenmed.sona.feature.download.work

import com.lhacenmed.sona.core.common.R as CommonR
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.lhacenmed.sona.core.common.notification.SonaNotificationAction
import com.lhacenmed.sona.core.common.notification.SonaNotificationChannel
import com.lhacenmed.sona.core.common.notification.SonaNotificationId
import com.lhacenmed.sona.core.common.notification.SonaNotifications
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.presentation
import com.lhacenmed.sona.feature.download.RunningDownload
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.ConcurrentHashMap

private const val EXTRA_DOWNLOAD_ID = "com.lhacenmed.sona.download.extra.ID"
private const val EXTRA_TITLE = "com.lhacenmed.sona.download.extra.TITLE"

/**
 * What downloads say in the shade, as Seal says it: each notification one of its own, none grouped by the app, so Android
 * bundles them all - running, paused, saved or failed - as it bundles any app's. The foreground service keeps one on a
 * channel of its own for as long as any download runs, never taken away and posted again: how many run - each says how fast
 * itself. Each download has its own while it runs, with how far it is, Pause and Cancel; then, in its place, whether it was
 * saved, in one that opens its sheet. Each is posted again every moment as it goes, keeping the time it was first shown - which
 * Android orders them by - so none takes another's place: the newest download first, how many run beneath them all.
 */
internal object DownloadNotifications {

    /** When the downloads running now began to run - the service's notification's time. */
    @Volatile
    private var runningSince = 0L

    /** When each download's notification was first shown, by its id - its time, while it runs. */
    private val shownSince = ConcurrentHashMap<Long, Long>()

    /** What the worker's foreground service shows for [running] - see [show]. */
    fun foregroundInfo(context: Context, running: List<RunningDownload>): ForegroundInfo {
        runningSince = System.currentTimeMillis()
        val notification = service(context, running)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(SonaNotificationId.DOWNLOAD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(SonaNotificationId.DOWNLOAD, notification)
        }
    }

    /**
     * Shows [running] as it stands: the foreground service's notification changed in place, and each download in its own,
     * taking away those of [shown] no longer running. The ids of their own now shown.
     */
    fun show(context: Context, running: List<RunningDownload>, shown: Set<Int>): Set<Int> {
        SonaNotifications.post(context, SonaNotificationId.DOWNLOAD, service(context, running))
        shownSince.keys.retainAll(running.map { it.id }.toSet())
        val own = running.associateBy { SonaNotificationId.download(it.id) }
        own.forEach { (id, download) -> SonaNotifications.post(context, id, runningNotification(context, download)) }
        clear(context, shown - own.keys)
        return own.keys
    }

    /** Takes [shown], downloads' own notifications, out of the shade. */
    fun clear(context: Context, shown: Set<Int>) = shown.forEach { SonaNotifications.cancel(context, it) }

    private fun service(context: Context, running: List<RunningDownload>): Notification =
        SonaNotifications.builder(context, SonaNotificationChannel.DOWNLOAD_SERVICE)
            .setContentTitle(
                if (running.isEmpty()) {
                    context.getString(R.string.download_starting)
                } else {
                    context.resources.getQuantityString(R.plurals.download_downloading_count, running.size, running.size)
                },
            )
            .setWhen(runningSince)
            .setShowWhen(false)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** Says [title] is paused, with Resume and Cancel - posted where it was paused from its notification. */
    fun postPaused(context: Context, downloadId: Long, title: String?) {
        val notification = SonaNotifications.builder(context, SonaNotificationChannel.DOWNLOADS)
            .setContentTitle(title ?: context.getString(R.string.download_starting))
            .setContentText(context.getString(R.string.download_paused))
            .setSilent(true)
            .apply { listOf(DownloadAction.RESUME, DownloadAction.CANCEL).forEach { addAction(it.iconRes, context.getString(it.labelRes), it.intent(context, downloadId, title)) } }
            .build()
        SonaNotifications.post(context, SonaNotificationId.downloadPaused(downloadId), notification)
    }

    /** Takes away the notification saying [downloadId] is paused - running again, or cancelled. */
    fun cancelPaused(context: Context, downloadId: Long) = SonaNotifications.cancel(context, SonaNotificationId.downloadPaused(downloadId))

    /**
     * Says download [downloadId], [title], was saved - or, for [failure], why not, as its row says it - in a notification of its
     * own in place of the one it ran in, kept beside every other's, that opens its sheet.
     */
    fun postResult(context: Context, downloadId: Long, title: String, failure: DownloadFailure?) {
        SonaNotifications.cancel(context, SonaNotificationId.download(downloadId))
        val notification = SonaNotifications.builder(context, SonaNotificationChannel.DOWNLOADS)
            .setContentTitle(title)
            .setContentText(context.getString(failure?.presentation?.rowRes ?: R.string.download_saved))
            .setContentIntent(SonaNotificationChannel.DOWNLOADS.entry.pendingIntent(context, itemId = downloadId))
            .setAutoCancel(true)
            .build()
        SonaNotifications.post(context, SonaNotificationId.downloadResult(downloadId), notification)
    }

    /** Takes away what was last said of [downloadId] - paused, saved or failed - as it runs again. */
    fun cancelSettled(context: Context, downloadId: Long) {
        cancelPaused(context, downloadId)
        SonaNotifications.cancel(context, SonaNotificationId.downloadResult(downloadId))
    }

    private fun runningNotification(context: Context, running: RunningDownload): Notification {
        val title = running.title ?: context.getString(R.string.download_starting)
        return SonaNotifications.progress(
            context,
            SonaNotificationChannel.DOWNLOADS,
            title = title,
            text = progressText(context, running),
            detail = null,
            percent = percentOf(running.downloadedBytes, running.totalBytes),
            actions = listOf(DownloadAction.PAUSE, DownloadAction.CANCEL).map {
                SonaNotificationAction(context.getString(it.labelRes), it.iconRes, it.intent(context, running.id, title))
            },
            // Bundled with the rest, as a live update never is.
            asLiveUpdate = false,
            since = shownSince.getOrPut(running.id) { System.currentTimeMillis() },
        )
    }

    /**
     * How far [running] is, in a line: which of a playlist's videos, how much of how much, how fast - "3/12 · 12 MB / 27 MB ·
     * 1.2 MB/s" - or that it is being made ready. The same bytes its percent is of.
     */
    private fun progressText(context: Context, running: RunningDownload): String? {
        if (running.isFinishingUp) return context.getString(R.string.download_finishing)
        fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
        return listOfNotNull(
            running.item?.let { item -> running.itemCount?.let { "$item/$it" } },
            running.downloadedBytes?.let { done -> running.totalBytes?.let { "${size(done)} / ${size(it)}" } ?: size(done) },
            running.bytesPerSecond?.let { context.getString(R.string.download_speed, size(it)) },
        ).joinToString(InfoSeparator).ifEmpty { null }
    }

    /** [done] of [total] bytes as a percent, never 100 before it is all there - null while the whole is unknown. */
    private fun percentOf(done: Long?, total: Long?): Int? =
        if (done == null || total == null || total <= 0) null else (done * 100 / total).toInt().coerceIn(0, 100)
}

/** What a download's notification offers - see [DownloadActionReceiver]. */
private enum class DownloadAction(@StringRes val labelRes: Int, val iconRes: Int) {
    PAUSE(R.string.download_pause, CommonR.drawable.icon_pause),
    RESUME(R.string.download_resume, CommonR.drawable.icon_play),
    CANCEL(R.string.download_cancel, CommonR.drawable.icon_close),
    ;

    val intentAction = "com.lhacenmed.sona.download.action.$name"

    /** This action on download [downloadId], named [title]. */
    fun intent(context: Context, downloadId: Long, title: String?): PendingIntent = PendingIntent.getBroadcast(
        context,
        // One for each download and action, so none replaces another's.
        (downloadId * entries.size + ordinal).toInt(),
        Intent(context, DownloadActionReceiver::class.java).setAction(intentAction).putExtra(EXTRA_DOWNLOAD_ID, downloadId).putExtra(EXTRA_TITLE, title),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Pause, Resume and Cancel, from a download's notification. */
internal class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = DownloadAction.entries.firstOrNull { it.intentAction == intent.action } ?: return
        val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1).takeIf { it >= 0 } ?: return
        val queue = EntryPointAccessors.fromApplication(context, DownloadEntryPoint::class.java).queue()
        when (action) {
            DownloadAction.PAUSE -> {
                queue.pause(listOf(id))
                DownloadNotifications.postPaused(context, id, intent.getStringExtra(EXTRA_TITLE))
            }
            DownloadAction.RESUME -> {
                DownloadNotifications.cancelPaused(context, id)
                queue.resume(listOf(id))
            }
            DownloadAction.CANCEL -> {
                DownloadNotifications.cancelPaused(context, id)
                queue.cancel(listOf(id))
            }
        }
    }
}
