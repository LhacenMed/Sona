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

private const val EXTRA_DOWNLOAD_ID = "com.lhacenmed.sona.download.extra.ID"
private const val EXTRA_TITLE = "com.lhacenmed.sona.download.extra.TITLE"

/** What every download's notification is gathered under, while more than one runs. */
private const val GROUP = "com.lhacenmed.sona.downloads"

/**
 * What downloads say in the shade. One notification stays for as long as any runs - the foreground service's own, never taken
 * away and posted again: the download itself while one runs, with how far it is, Pause and Cancel - on Android 16 and later a
 * live update, its percent in the status bar - and, while several run, their summary, each download in a notification of its
 * own gathered under it. Then whether each was saved, in one of its own that opens its sheet.
 */
internal object DownloadNotifications {

    /** What the worker's foreground service shows for [running] - see [show]. */
    fun foregroundInfo(context: Context, running: List<RunningDownload>): ForegroundInfo {
        val notification = foreground(context, running)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(SonaNotificationId.DOWNLOAD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(SonaNotificationId.DOWNLOAD, notification)
        }
    }

    /**
     * Shows [running] as it stands: in the foreground service's notification, changed in place - and, while more than one
     * runs, each in its own beside it, taking away those of [shown] no longer running. The ids of their own now shown.
     */
    fun show(context: Context, running: List<RunningDownload>, shown: Set<Int>): Set<Int> {
        SonaNotifications.post(context, SonaNotificationId.DOWNLOAD, foreground(context, running))
        val own = if (running.size > 1) running.associateBy { SonaNotificationId.download(it.id) } else emptyMap()
        own.forEach { (id, download) -> SonaNotifications.post(context, id, runningNotification(context, download, isGrouped = true)) }
        clear(context, shown - own.keys)
        return own.keys
    }

    /** Takes [shown], downloads' own notifications, out of the shade. */
    fun clear(context: Context, shown: Set<Int>) = shown.forEach { SonaNotifications.cancel(context, it) }

    private fun foreground(context: Context, running: List<RunningDownload>): Notification = when (running.size) {
        0 -> SonaNotifications.progress(
            context,
            SonaNotificationChannel.DOWNLOADS,
            title = context.getString(R.string.download_starting),
            text = null,
            detail = null,
            percent = null,
            actions = emptyList(),
        )
        1 -> runningNotification(context, running.single(), isGrouped = false)
        else -> {
            // How far they all are, where every one of them knows its size.
            val total = running.map { it.totalBytes }.takeIf { sizes -> sizes.all { it != null } }?.sumOf { it ?: 0 }
            val speed = running.sumOf { it.bytesPerSecond ?: 0 }.takeIf { it > 0 }
            SonaNotifications.progress(
                context,
                SonaNotificationChannel.DOWNLOADS,
                title = context.resources.getQuantityString(R.plurals.download_downloading_count, running.size, running.size),
                text = speed?.let { context.getString(R.string.download_speed, Formatter.formatShortFileSize(context, it)) },
                detail = null,
                percent = percentOf(running.sumOf { it.downloadedBytes ?: 0 }, total),
                actions = emptyList(),
                group = GROUP,
                isGroupSummary = true,
            )
        }
    }

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
     * own, kept beside every other's, that opens its sheet.
     */
    fun postResult(context: Context, downloadId: Long, title: String, failure: DownloadFailure?) {
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

    private fun runningNotification(context: Context, running: RunningDownload, isGrouped: Boolean): Notification {
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
            group = GROUP.takeIf { isGrouped },
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
