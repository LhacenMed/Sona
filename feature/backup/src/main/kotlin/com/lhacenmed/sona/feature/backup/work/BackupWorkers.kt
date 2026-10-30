package com.lhacenmed.sona.feature.backup.work

import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.StringRes
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lhacenmed.sona.core.common.notification.SonaNotificationChannel
import com.lhacenmed.sona.core.common.notification.SonaNotificationId
import com.lhacenmed.sona.core.common.notification.SonaNotifications
import com.lhacenmed.sona.feature.backup.BackupOutcome
import com.lhacenmed.sona.feature.backup.BackupProgress
import com.lhacenmed.sona.feature.backup.BackupRepository
import com.lhacenmed.sona.feature.backup.R
import com.lhacenmed.sona.feature.backup.drive.DriveAccessLostException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

/** Makes a backup - one the user started, in the foreground, or an automatic one, which keeps quiet. */
internal class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** What stops this backup, from its notification. */
    private val stop get() = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

    override suspend fun getForegroundInfo() =
        foregroundInfo(applicationContext, R.string.backup_notification_title, null, isRestore = false, stop = stop)

    override suspend fun doWork(): Result {
        val isAutomatic = inputData.getBoolean(KEY_AUTOMATIC, false)
        // In the foreground, a run has no time limit - which an upload of a whole library can need. One the user
        // started always may; an automatic one asks, and runs on in the background where Android says no.
        val isForeground = if (isAutomatic) runsInForeground() else true.also { setForeground(getForegroundInfo()) }
        val reporter = ProgressReporter(this, R.string.backup_notification_title, isRestore = false, showsNotification = isForeground, stop = stop)
        return try {
            val outcome = repositoryOf(applicationContext).backUp(skipIfUnchanged = isAutomatic, onProgress = reporter::report)
            // Files that waited for Wi-Fi, or failed this time, go with the next backup on Wi-Fi, whenever that is.
            if (outcome == BackupOutcome.SAVED_FILES_WAITING || outcome == BackupOutcome.SAVED_FILES_LEFT) {
                BackupWork.backUpOnWifi(applicationContext)
            }
            Result.success(workDataOf(KEY_OUTCOME to outcome.name))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            // An automatic backup is tried again later; one the user asked for says it failed, and why where it can.
            if (isAutomatic) Result.retry() else failure(e)
        }
    }
}

/** Moves this worker to the foreground where Android allows it. Whether it did. */
private suspend fun CoroutineWorker.runsInForeground(): Boolean = try {
    setForeground(getForegroundInfo())
    true
} catch (_: IllegalStateException) {
    // ForegroundServiceStartNotAllowedException, from the background on Android 12 and later.
    false
}

/** Restores the backup last opened. Started again from the beginning after being cut short, it finishes the job. */
internal class RestoreWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo() =
        foregroundInfo(applicationContext, R.string.restore_notification_title, null, isRestore = true, stop = null)

    override suspend fun doWork(): Result {
        val reporter = ProgressReporter(this, R.string.restore_notification_title, isRestore = true, showsNotification = true, stop = null)
        setForeground(getForegroundInfo())
        return try {
            repositoryOf(applicationContext).restore(onProgress = reporter::report)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            failure(e)
        }
    }
}

/** A run failed by [exception] - with the reason, where it is one the user can act on, else with what went wrong. */
private fun failure(exception: Exception): ListenableWorker.Result {
    val reason = when (exception) {
        is DriveAccessLostException -> BackupFailure.DRIVE_ACCESS_LOST
        else -> return ListenableWorker.Result.failure(workDataOf(KEY_FAILURE_DETAIL to exception.detail()))
    }
    return ListenableWorker.Result.failure(workDataOf(KEY_FAILURE to reason.name))
}

/** What went wrong, in a line: the kind of failure and its message, short enough to show. */
private fun Exception.detail(): String = listOfNotNull(this::class.simpleName, message).joinToString(": ").take(MAX_DETAIL_LENGTH)

private const val MAX_DETAIL_LENGTH = 200

/** What [progress] says it is doing, for its notification and its screen. */
fun backupProgressText(context: Context, progress: BackupProgress?, isRestore: Boolean): String? = when (progress) {
    null -> null
    is BackupProgress.Fingerprinting -> context.getString(R.string.backup_progress_fingerprinting, progress.done, progress.total)
    BackupProgress.Working -> context.getString(if (isRestore) R.string.restore_progress_library else R.string.backup_progress_writing)
    is BackupProgress.RestoringFiles -> context.getString(R.string.restore_progress_files, progress.done, progress.total)
    is BackupProgress.UploadingFiles -> context.getString(R.string.backup_progress_uploading, progress.done, progress.total)
}

/**
 * Passes a run's progress on - to its screen, and to its notification where it has one - only as it moves by a
 * percent or changes step, so reading thousands of files does not post thousands of updates.
 */
private class ProgressReporter(
    private val worker: CoroutineWorker,
    @StringRes private val titleRes: Int,
    private val isRestore: Boolean,
    private val showsNotification: Boolean,
    private val stop: PendingIntent?,
) {
    private var reported: Pair<String, Int>? = null

    // Uploads report from several at once.
    @Synchronized
    fun report(progress: BackupProgress) {
        val percent = progress.fraction()?.let { (it * 100).roundToInt() } ?: -1
        val step = progress::class.simpleName.orEmpty() to percent
        if (step == reported) return
        reported = step
        worker.setProgressAsync(progress.toData())
        if (showsNotification) worker.setForegroundAsync(foregroundInfo(worker.applicationContext, titleRes, progress, isRestore, stop))
    }
}

private fun BackupProgress.fraction(): Float? = when (this) {
    is BackupProgress.Fingerprinting -> if (total > 0) done.toFloat() / total else null
    is BackupProgress.RestoringFiles -> if (total > 0) done.toFloat() / total else null
    is BackupProgress.UploadingFiles -> if (total > 0) done.toFloat() / total else null
    BackupProgress.Working -> null
}

/** The run's notification - with a Stop action, where it can be stopped. */
private fun foregroundInfo(
    context: Context,
    @StringRes titleRes: Int,
    progress: BackupProgress?,
    isRestore: Boolean,
    stop: PendingIntent?,
): ForegroundInfo {
    val fraction = progress?.fraction()
    val notification = SonaNotifications.builder(context, SonaNotificationChannel.BACKUP)
        .setContentTitle(context.getString(titleRes))
        .setContentText(backupProgressText(context, progress, isRestore))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
        .apply { stop?.let { addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.backup_notification_stop), it) } }
        .build()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(SonaNotificationId.BACKUP, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(SonaNotificationId.BACKUP, notification)
    }
}

private fun repositoryOf(context: Context): BackupRepository =
    EntryPointAccessors.fromApplication(context, BackupEntryPoint::class.java).backupRepository()

/** The workers are made by WorkManager, not Hilt, so they reach the repository through here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BackupEntryPoint {
    fun backupRepository(): BackupRepository
}
