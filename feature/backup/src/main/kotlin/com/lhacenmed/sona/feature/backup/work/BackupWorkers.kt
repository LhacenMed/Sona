package com.lhacenmed.sona.feature.backup.work

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lhacenmed.sona.feature.backup.BackupOutcome
import com.lhacenmed.sona.feature.backup.BackupProgress
import com.lhacenmed.sona.feature.backup.BackupRepository
import com.lhacenmed.sona.feature.backup.BackupSettings
import com.lhacenmed.sona.feature.backup.BackupStep
import com.lhacenmed.sona.feature.backup.R
import com.lhacenmed.sona.feature.backup.drive.DriveAccessLostException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

/** How often a run says where it is within a step - enough for the speed and the bytes to move with the upload. */
private const val REPORT_INTERVAL_MS = 1_000L

/** Makes a backup - one the user started, in the foreground, or an automatic one, which keeps quiet. */
internal class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo() =
        foregroundInfo(applicationContext, R.string.backup_notification_title, null, isPausable = true)

    override suspend fun doWork(): Result {
        val isAutomatic = inputData.getBoolean(KEY_AUTOMATIC, false)
        // Whichever backup runs carries on the one paused, from where it stopped - and is the word on the last one failed.
        BackupWork.leavePause(applicationContext)
        cancelFailedNotification(applicationContext)
        // In the foreground, a run has no time limit - which an upload of a whole library can need. One the user
        // started always may; an automatic one asks, and runs on in the background where Android says no.
        val isForeground = if (isAutomatic) runsInForeground() else true.also { setForeground(getForegroundInfo()) }
        val reporter = ProgressReporter(this, R.string.backup_notification_title, isPausable = true, showsNotification = isForeground)
        return try {
            val outcome = repositoryOf(applicationContext).backUp(onProgress = reporter::report)
            // Files that waited for Wi-Fi, or failed this time, go with the next backup on Wi-Fi, whenever that is.
            if (outcome == BackupOutcome.SAVED_FILES_WAITING || outcome == BackupOutcome.SAVED_FILES_LEFT) {
                BackupWork.backUpOnWifi(applicationContext)
            }
            Result.success(workDataOf(KEY_OUTCOME to outcome.name))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            // An automatic backup is tried again later; one the user asked for says it failed, and why where it can.
            if (isAutomatic) Result.retry() else failure(e, R.string.backup_failed_title, canRetry = true)
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
        foregroundInfo(applicationContext, R.string.restore_notification_title, null, isPausable = false)

    override suspend fun doWork(): Result {
        val reporter = ProgressReporter(this, R.string.restore_notification_title, isPausable = false, showsNotification = true)
        cancelFailedNotification(applicationContext)
        setForeground(getForegroundInfo())
        return try {
            repositoryOf(applicationContext).restore(onProgress = reporter::report)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            failure(e, R.string.restore_failed_title, canRetry = false)
        }
    }
}

/**
 * A run failed by [exception] - with the reason, where it is one the user can act on, else with what went wrong -
 * said in a notification too, titled [titleRes], with Try again where it [canRetry]: the run may have been left
 * long before, the screen that started it closed.
 */
private fun CoroutineWorker.failure(exception: Exception, @StringRes titleRes: Int, canRetry: Boolean): ListenableWorker.Result {
    if (exception is DriveAccessLostException) {
        postFailedNotification(applicationContext, titleRes, applicationContext.getString(R.string.backup_failed_drive_access), canRetry = false)
        return ListenableWorker.Result.failure(workDataOf(KEY_FAILURE to BackupFailure.DRIVE_ACCESS_LOST.name))
    }
    val detail = exception.detail()
    postFailedNotification(applicationContext, titleRes, detail, canRetry)
    return ListenableWorker.Result.failure(workDataOf(KEY_FAILURE_DETAIL to detail))
}

/** What went wrong, in a line: the kind of failure and its message, short enough to show. */
private fun Exception.detail(): String = listOfNotNull(this::class.simpleName, message).joinToString(": ").take(MAX_DETAIL_LENGTH)

private const val MAX_DETAIL_LENGTH = 200

/**
 * Passes a run's progress on - to its screen, and to its notification where it has one - at each new step, and
 * within one every [REPORT_INTERVAL_MS], so a byte-by-byte upload does not post thousands of updates.
 */
private class ProgressReporter(
    private val worker: CoroutineWorker,
    @StringRes private val titleRes: Int,
    private val isPausable: Boolean,
    private val showsNotification: Boolean,
) {
    private var reportedStep: BackupStep? = null
    private var reportedAt = 0L

    // Uploads report from several at once.
    @Synchronized
    fun report(progress: BackupProgress) {
        val now = SystemClock.elapsedRealtime()
        if (progress.step == reportedStep && now - reportedAt < REPORT_INTERVAL_MS) return
        reportedStep = progress.step
        reportedAt = now
        worker.setProgressAsync(progress.toData())
        if (showsNotification) worker.setForegroundAsync(foregroundInfo(worker.applicationContext, titleRes, progress, isPausable))
    }
}

private fun repositoryOf(context: Context): BackupRepository =
    EntryPointAccessors.fromApplication(context, BackupEntryPoint::class.java).backupRepository()

/** The workers and the notification's actions are made by Android, not Hilt, so they reach what they need through here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BackupEntryPoint {
    fun backupRepository(): BackupRepository

    fun backupSettings(): BackupSettings
}
