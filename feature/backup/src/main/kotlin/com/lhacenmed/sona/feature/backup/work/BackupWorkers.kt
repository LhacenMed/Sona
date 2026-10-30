package com.lhacenmed.sona.feature.backup.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.StringRes
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lhacenmed.sona.core.common.notification.SonaNotificationChannel
import com.lhacenmed.sona.core.common.notification.SonaNotificationId
import com.lhacenmed.sona.core.common.notification.SonaNotifications
import com.lhacenmed.sona.feature.backup.BackupProgress
import com.lhacenmed.sona.feature.backup.BackupRepository
import com.lhacenmed.sona.feature.backup.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

/** Makes a backup - one the user started, in the foreground, or an automatic one, which keeps quiet. */
internal class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo() = foregroundInfo(applicationContext, R.string.backup_notification_title, null, isRestore = false)

    override suspend fun doWork(): Result {
        val isAutomatic = inputData.getBoolean(KEY_AUTOMATIC, false)
        val reporter = ProgressReporter(this, R.string.backup_notification_title, isRestore = false, showsNotification = !isAutomatic)
        if (!isAutomatic) setForeground(getForegroundInfo())
        return try {
            repositoryOf(applicationContext).backUp(skipIfUnchanged = isAutomatic, onProgress = reporter::report)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // An automatic backup is tried again later; one the user asked for says it failed.
            if (isAutomatic) Result.retry() else Result.failure()
        }
    }
}

/** Restores the backup last opened. Started again from the beginning after being cut short, it finishes the job. */
internal class RestoreWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo() = foregroundInfo(applicationContext, R.string.restore_notification_title, null, isRestore = true)

    override suspend fun doWork(): Result {
        val reporter = ProgressReporter(this, R.string.restore_notification_title, isRestore = true, showsNotification = true)
        setForeground(getForegroundInfo())
        return try {
            repositoryOf(applicationContext).restore(onProgress = reporter::report)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.failure()
        }
    }
}

/** What [progress] says it is doing, for its notification and its screen. */
fun backupProgressText(context: Context, progress: BackupProgress?, isRestore: Boolean): String? = when (progress) {
    null -> null
    is BackupProgress.Fingerprinting -> context.getString(R.string.backup_progress_fingerprinting, progress.done, progress.total)
    BackupProgress.Working -> context.getString(if (isRestore) R.string.restore_progress_library else R.string.backup_progress_writing)
    is BackupProgress.RestoringFiles -> context.getString(R.string.restore_progress_files, progress.done, progress.total)
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
) {
    private var reported: Pair<String, Int>? = null

    fun report(progress: BackupProgress) {
        val percent = progress.fraction()?.let { (it * 100).roundToInt() } ?: -1
        val step = progress::class.simpleName.orEmpty() to percent
        if (step == reported) return
        reported = step
        worker.setProgressAsync(progress.toData())
        if (showsNotification) worker.setForegroundAsync(foregroundInfo(worker.applicationContext, titleRes, progress, isRestore))
    }
}

private fun BackupProgress.fraction(): Float? = when (this) {
    is BackupProgress.Fingerprinting -> if (total > 0) done.toFloat() / total else null
    is BackupProgress.RestoringFiles -> if (total > 0) done.toFloat() / total else null
    BackupProgress.Working -> null
}

private fun foregroundInfo(context: Context, @StringRes titleRes: Int, progress: BackupProgress?, isRestore: Boolean): ForegroundInfo {
    val fraction = progress?.fraction()
    val notification = SonaNotifications.builder(context, SonaNotificationChannel.BACKUP)
        .setContentTitle(context.getString(titleRes))
        .setContentText(backupProgressText(context, progress, isRestore))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
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
