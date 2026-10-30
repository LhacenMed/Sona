package com.lhacenmed.sona.feature.backup.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lhacenmed.sona.feature.backup.BackupProgress
import com.lhacenmed.sona.feature.backup.BackupSchedule
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val AUTOMATIC_BACKUP = "automatic_backup"
private const val BACKUP = "backup"
private const val RESTORE = "restore"

internal const val KEY_AUTOMATIC = "automatic"
private const val KEY_STEP = "step"
private const val KEY_DONE = "done"
private const val KEY_TOTAL = "total"

/** A backup or a restore the user started, as its screen follows it. */
sealed interface BackupRun {
    /** Under way - at [progress], once it has said where it is. */
    data class Running(val progress: BackupProgress?) : BackupRun

    data object Succeeded : BackupRun

    data object Failed : BackupRun
}

/**
 * Every backup and restore is run here, by WorkManager: one the user starts as a foreground job with its
 * progress in a notification, so it outlives the screen and the app being closed; an automatic one quietly,
 * while the battery and storage allow. Each kind runs once at a time.
 */
object BackupWork {

    /** Keeps automatic backups made as often as [schedule] says - or none, while it is off. */
    fun follow(context: Context, schedule: BackupSchedule) {
        val workManager = WorkManager.getInstance(context)
        val intervalDays = schedule.intervalDays ?: return run { workManager.cancelUniqueWork(AUTOMATIC_BACKUP) }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalDays, TimeUnit.DAYS)
            .setInputData(workDataOf(KEY_AUTOMATIC to true))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(AUTOMATIC_BACKUP, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun backUp(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(BACKUP, ExistingWorkPolicy.KEEP, userRequest<BackupWorker>())
    }

    /** Restores the backup last opened, with the files chosen for it - see [com.lhacenmed.sona.feature.backup.BackupRepository.restore]. */
    fun restore(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(RESTORE, ExistingWorkPolicy.KEEP, userRequest<RestoreWorker>())
    }

    /** The latest backup the user started, or null before any. */
    fun backupRun(context: Context): Flow<BackupRun?> = runOf(context, BACKUP)

    /** The latest restore, or null before any. */
    fun restoreRun(context: Context): Flow<BackupRun?> = runOf(context, RESTORE)

    private inline fun <reified W : ListenableWorker> userRequest() =
        OneTimeWorkRequestBuilder<W>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()

    private fun runOf(context: Context, name: String): Flow<BackupRun?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(name).map { infos ->
            val info = infos.lastOrNull() ?: return@map null
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> BackupRun.Running(info.progress.toBackupProgress())
                WorkInfo.State.SUCCEEDED -> BackupRun.Succeeded
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> BackupRun.Failed
            }
        }
}

internal fun BackupProgress.toData(): Data = when (this) {
    is BackupProgress.Fingerprinting -> workDataOf(KEY_STEP to "fingerprinting", KEY_DONE to done, KEY_TOTAL to total)
    BackupProgress.Working -> workDataOf(KEY_STEP to "working")
    is BackupProgress.RestoringFiles -> workDataOf(KEY_STEP to "restoringFiles", KEY_DONE to done, KEY_TOTAL to total)
}

private fun Data.toBackupProgress(): BackupProgress? = when (getString(KEY_STEP)) {
    "fingerprinting" -> BackupProgress.Fingerprinting(getInt(KEY_DONE, 0), getInt(KEY_TOTAL, 0))
    "working" -> BackupProgress.Working
    "restoringFiles" -> BackupProgress.RestoringFiles(getInt(KEY_DONE, 0), getInt(KEY_TOTAL, 0))
    else -> null
}
