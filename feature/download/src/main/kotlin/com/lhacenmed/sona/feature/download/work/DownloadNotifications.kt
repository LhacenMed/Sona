package com.lhacenmed.sona.feature.download.work

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.StringRes
import androidx.work.ForegroundInfo
import com.lhacenmed.sona.core.common.notification.SonaNotificationAction
import com.lhacenmed.sona.core.common.notification.SonaNotificationChannel
import com.lhacenmed.sona.core.common.notification.SonaNotificationId
import com.lhacenmed.sona.core.common.notification.SonaNotifications
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.presentation
import com.lhacenmed.sona.feature.download.RunningDownload
import dagger.hilt.android.EntryPointAccessors

private const val EXTRA_DOWNLOAD_ID = "com.lhacenmed.sona.download.extra.ID"
private const val EXTRA_TITLE = "com.lhacenmed.sona.download.extra.TITLE"

/**
 * What a download says in the shade: each running download its own notification - as YTDLnis's - with how far it is and the
 * last line the downloader wrote, as Seal's, and Pause and Cancel; on Android 16 and later a live update, its percent in the
 * status bar. Then whether each was saved.
 */
internal object DownloadNotifications {

    /** The first running download's notification, as the worker's foreground service shows it - the others are [showOthers]. */
    fun foregroundInfo(context: Context, first: RunningDownload?): ForegroundInfo {
        val notification = first?.let { runningNotification(context, it) } ?: SonaNotifications.progress(
            context,
            SonaNotificationChannel.DOWNLOADS,
            title = context.getString(R.string.download_starting),
            text = null,
            detail = null,
            percent = null,
            actions = emptyList(),
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(SonaNotificationId.DOWNLOAD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(SonaNotificationId.DOWNLOAD, notification)
        }
    }

    /**
     * Shows [others] - the downloads running beside the first - each in its own notification, taking away those of [shown] no
     * longer among them. The ids now shown.
     */
    fun showOthers(context: Context, others: Collection<RunningDownload>, shown: Set<Int>): Set<Int> {
        val showing = others.associateBy { SonaNotificationId.download(it.id) }
        showing.forEach { (id, running) -> SonaNotifications.post(context, id, runningNotification(context, running)) }
        (shown - showing.keys).forEach { SonaNotifications.cancel(context, it) }
        return showing.keys
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

    /** Says [title] was saved - or, for [failure], why not, as its row says it. */
    fun postResult(context: Context, title: String, failure: DownloadFailure?) {
        val notification = SonaNotifications.builder(context, SonaNotificationChannel.DOWNLOADS)
            .setContentTitle(title)
            .setContentText(context.getString(failure?.presentation?.rowRes ?: R.string.download_saved))
            .setAutoCancel(true)
            .build()
        SonaNotifications.post(context, SonaNotificationId.DOWNLOAD_RESULT, notification)
    }

    private fun runningNotification(context: Context, running: RunningDownload): Notification {
        val title = running.title ?: context.getString(R.string.download_starting)
        return SonaNotifications.progress(
            context,
            SonaNotificationChannel.DOWNLOADS,
            title = title,
            // The downloader's own words, without the part of it that names which of its parts is speaking.
            text = running.line?.replace(SpeakerTag, "")?.ifBlank { null },
            detail = null,
            percent = running.fraction?.let { (it * 100).toInt() },
            actions = listOf(DownloadAction.PAUSE, DownloadAction.CANCEL).map {
                SonaNotificationAction(context.getString(it.labelRes), it.iconRes, it.intent(context, running.id, title))
            },
        )
    }
}

/** "[download] " - what starts each line the downloader writes. */
private val SpeakerTag = Regex("""^\[[^]]*]\s*""")

/** What a download's notification offers - see [DownloadActionReceiver]. */
private enum class DownloadAction(@StringRes val labelRes: Int, val iconRes: Int) {
    PAUSE(R.string.download_pause, android.R.drawable.ic_media_pause),
    RESUME(R.string.download_resume, android.R.drawable.ic_media_play),
    CANCEL(R.string.download_cancel, android.R.drawable.ic_menu_close_clear_cancel),
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
