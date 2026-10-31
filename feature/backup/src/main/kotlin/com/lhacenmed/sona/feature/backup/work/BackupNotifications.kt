package com.lhacenmed.sona.feature.backup.work

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.lhacenmed.sona.core.common.notification.SonaNotificationChannel
import com.lhacenmed.sona.core.common.notification.SonaNotificationId
import com.lhacenmed.sona.core.common.notification.SonaNotifications
import com.lhacenmed.sona.feature.backup.BackupProgress
import com.lhacenmed.sona.feature.backup.R
import com.lhacenmed.sona.feature.backup.Transfer
import kotlin.math.roundToInt

private const val PROGRESS_MAX = 100

/** What a step says it is doing, with how many of how many where it counts: "Uploading music and videos · 12 of 800". */
fun backupProgressText(context: Context, progress: BackupProgress?): String? = progress?.let {
    val label = context.getString(it.step.labelRes)
    if (it.total > 0) context.getString(R.string.backup_progress_count, label, it.done, it.total) else label
}

/** [backupProgressText], with its percent while bytes move: "Uploading music and videos · 12 of 800 · 42%". */
fun backupProgressSummary(context: Context, progress: BackupProgress?): String? {
    val text = backupProgressText(context, progress) ?: return null
    val percent = backupProgressPercent(progress)?.takeIf { progress?.transfer != null } ?: return text
    return listOf(text, context.getString(R.string.backup_progress_percent, percent)).joinToString(" · ")
}

/** [progress] as a whole percent, where it can tell. */
private fun backupProgressPercent(progress: BackupProgress?): Int? = progress?.fraction?.let { (it * PROGRESS_MAX).roundToInt() }

/** "340 MB of 780 MB · 1.5 MB/s" - short enough for the notification's top line. */
private fun transferText(context: Context, transfer: Transfer): String = context.getString(
    R.string.backup_progress_transfer,
    Formatter.formatShortFileSize(context, transfer.done),
    Formatter.formatShortFileSize(context, transfer.total),
    Formatter.formatShortFileSize(context, transfer.bytesPerSecond),
)

/**
 * A run's notification, as its foreground service shows it - with Pause and Stop where it [isPausable]. On Android
 * 16 and later it is a promoted live update, its percent the title, which the system shows in the status bar too.
 */
internal fun foregroundInfo(context: Context, @StringRes titleRes: Int, progress: BackupProgress?, isPausable: Boolean): ForegroundInfo {
    val notification = runningNotification(context, context.getString(titleRes), progress, isPausable)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(SonaNotificationId.BACKUP, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(SonaNotificationId.BACKUP, notification)
    }
}

private fun runningNotification(context: Context, title: String, progress: BackupProgress?, isPausable: Boolean): Notification {
    val percent = backupProgressPercent(progress)
    val text = backupProgressText(context, progress) ?: title
    val detail = progress?.transfer?.let { transferText(context, it) }
    val actions = if (isPausable) listOf(BackupAction.PAUSE, BackupAction.STOP) else emptyList()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && SonaNotifications.canPostPromoted(context)) {
        val progressStyle = Notification.ProgressStyle()
            .setProgressPoints(listOf(Notification.ProgressStyle.Point(0), Notification.ProgressStyle.Point(PROGRESS_MAX)))
            .setProgress(percent ?: 0)
            .setProgressIndeterminate(percent == null)
        return SonaNotifications.platformBuilder(context, SonaNotificationChannel.BACKUP)
            .setContentTitle(percent?.let { context.getString(R.string.backup_progress_percent, it) } ?: title)
            .setContentText(text)
            .setSubText(detail ?: title)
            .setStyle(progressStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            // Asks for the notification to be promoted to a live update.
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            .apply {
                actions.forEach { action ->
                    addAction(Notification.Action.Builder(Icon.createWithResource(context, action.iconRes), context.getString(action.labelRes), action.intent(context)).build())
                }
            }
            .build()
    }
    return SonaNotifications.builder(context, SonaNotificationChannel.BACKUP)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(text, detail).joinToString("\n")))
        .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .apply { actions.forEach { addAction(it.iconRes, context.getString(it.labelRes), it.intent(context)) } }
        .build()
}

/** Says the backup is paused, with Resume and Stop - until it is resumed, stopped, or started again. */
internal fun postPausedNotification(context: Context) {
    val notification = SonaNotifications.builder(context, SonaNotificationChannel.BACKUP)
        .setContentTitle(context.getString(R.string.backup_paused_title))
        .setContentText(context.getString(R.string.backup_paused_text))
        .apply {
            listOf(BackupAction.RESUME, BackupAction.STOP).forEach { addAction(it.iconRes, context.getString(it.labelRes), it.intent(context)) }
        }
        .build()
    SonaNotifications.post(context, SonaNotificationId.BACKUP_PAUSED, notification)
}

internal fun cancelPausedNotification(context: Context) = SonaNotifications.cancel(context, SonaNotificationId.BACKUP_PAUSED)

/**
 * Says a backup or a restore the user started failed - titled by [titleRes], with [reason] in full - opening the app
 * with a press, and, for a backup, with Try again. Left until pressed away, or the next run starts.
 */
internal fun postFailedNotification(context: Context, @StringRes titleRes: Int, reason: String, canRetry: Boolean) {
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
    val notification = SonaNotifications.builder(context, SonaNotificationChannel.BACKUP)
        .setContentTitle(context.getString(titleRes))
        .setContentText(reason)
        .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
        .setCategory(NotificationCompat.CATEGORY_ERROR)
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .apply { if (canRetry) BackupAction.RETRY.let { addAction(it.iconRes, context.getString(it.labelRes), it.intent(context)) } }
        .build()
    SonaNotifications.post(context, SonaNotificationId.BACKUP_FAILED, notification)
}

internal fun cancelFailedNotification(context: Context) = SonaNotifications.cancel(context, SonaNotificationId.BACKUP_FAILED)

/** What a backup's notification offers - see [BackupActionReceiver]. */
internal enum class BackupAction(@StringRes val labelRes: Int, val iconRes: Int) {
    PAUSE(R.string.backup_notification_pause, android.R.drawable.ic_media_pause),
    RESUME(R.string.backup_notification_resume, android.R.drawable.ic_media_play),
    STOP(R.string.backup_notification_stop, android.R.drawable.ic_menu_close_clear_cancel),
    RETRY(R.string.backup_notification_retry, android.R.drawable.ic_popup_sync),
    ;

    val intentAction = "com.lhacenmed.sona.backup.action.$name"

    fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, BackupActionReceiver::class.java).setAction(intentAction),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
