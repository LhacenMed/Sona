package com.lhacenmed.sona.feature.backup.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lhacenmed.sona.feature.backup.BackupOutcome
import com.lhacenmed.sona.feature.backup.BackupProgress
import com.lhacenmed.sona.feature.backup.BackupSchedule
import com.lhacenmed.sona.feature.backup.BackupSettings
import com.lhacenmed.sona.feature.backup.BackupStep
import com.lhacenmed.sona.feature.backup.Transfer
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val AUTOMATIC_BACKUP = "automatic_backup"
private const val BACKUP = "backup"
private const val BACKUP_ON_WIFI = "backup_on_wifi"
private const val RESTORE = "restore"

internal const val KEY_AUTOMATIC = "automatic"
private const val KEY_STEP = "step"
private const val KEY_DONE = "done"
private const val KEY_TOTAL = "total"
private const val KEY_BYTES_DONE = "bytesDone"
private const val KEY_BYTES_TOTAL = "bytesTotal"
private const val KEY_BYTES_PER_SECOND = "bytesPerSecond"
private const val KEY_BYTES_SENT = "bytesSent"
internal const val KEY_FAILURE = "failure"
internal const val KEY_FAILURE_DETAIL = "failureDetail"
internal const val KEY_OUTCOME = "outcome"

/** Why a backup or a restore failed, where it is something the user can act on. */
enum class BackupFailure {
    /** Access to Google Drive was taken back from the user's Google Account. */
    DRIVE_ACCESS_LOST,
}

/** A backup or a restore, as its screen follows it - one the user started, or an automatic backup while it runs. */
sealed interface BackupRun {
    /**
     * Under way - at [progress], once it has said where it is. [isAutomatic] for a backup the schedule started, whose end is
     * not kept to be told: once over, the screen shows the last one the user started again.
     */
    data class Running(val progress: BackupProgress?, val isAutomatic: Boolean = false) : BackupRun

    /** Done - a backup, as [outcome] says; a restore, with none. */
    data class Succeeded(val outcome: BackupOutcome?) : BackupRun

    /** Failed - for [reason], where it is one the user can act on; else with [detail], what went wrong, where known. */
    data class Failed(val reason: BackupFailure?, val detail: String?) : BackupRun

    /** Stopped by the user. */
    data object Stopped : BackupRun

    /** Paused by the user, to carry on from where it stopped once resumed. */
    data object Paused : BackupRun
}

/**
 * Every backup and restore is run here, by WorkManager: one the user starts as a foreground job with its
 * progress in a notification, so it outlives the screen and the app being closed; an automatic one quietly,
 * while the battery and storage allow. Each kind runs once at a time.
 */
object BackupWork {

    /**
     * Keeps automatic backups made as often as [schedule] says - or none, while it is off. The first comes a whole interval
     * after the schedule is turned on, never there and then: turned on at the end of the setup, or right after a restore,
     * there is time to choose what is backed up, and nothing just restored is sent straight back. A schedule already
     * followed keeps its own timing.
     */
    fun follow(context: Context, schedule: BackupSchedule) {
        val workManager = WorkManager.getInstance(context)
        val intervalDays = schedule.intervalDays ?: return run { workManager.cancelUniqueWork(AUTOMATIC_BACKUP) }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalDays, TimeUnit.DAYS)
            .setInitialDelay(intervalDays, TimeUnit.DAYS)
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

    /**
     * Pauses the backup under way - the user's, or an automatic one. Resumed, it carries on from where it stopped: the
     * files already in Drive are not sent again, and one cut short goes on from the very byte Drive has of it.
     */
    suspend fun pauseBackup(context: Context) {
        settingsOf(context).setBackupPaused(true)
        cancelBackups(context)
        postPausedNotification(context)
    }

    /** Carries on with the backup [pauseBackup] paused. */
    suspend fun resumeBackup(context: Context) {
        leavePause(context)
        backUp(context)
    }

    /**
     * Stops the backup under way - the user's, or an automatic one - or the one paused. Nothing is left half done: a backup
     * is kept only once written whole, files already in Drive stay there for the next one, and an upload cut short is let
     * go of by Drive.
     */
    suspend fun stopBackup(context: Context) {
        leavePause(context)
        cancelBackups(context)
    }

    /**
     * Cancels every backup under way. An automatic one stops for this time only: the schedule is followed afresh, the next
     * a whole interval from now.
     */
    private suspend fun cancelBackups(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(BACKUP)
        workManager.cancelUniqueWork(BACKUP_ON_WIFI)
        if (isRunning(workManager, AUTOMATIC_BACKUP)) {
            workManager.cancelUniqueWork(AUTOMATIC_BACKUP)
            follow(context, settingsOf(context).scheduleNow())
        }
    }

    private suspend fun isRunning(workManager: WorkManager, name: String): Boolean =
        workManager.getWorkInfosForUniqueWorkFlow(name).first().any { it.state == WorkInfo.State.RUNNING }

    /** No backup is paused any more: one is under way again, or none is wanted. */
    internal suspend fun leavePause(context: Context) {
        settingsOf(context).setBackupPaused(false)
        cancelPausedNotification(context)
    }

    /** Backs up again - quietly - once the device is on Wi-Fi, for a backup whose music and videos did not all go. */
    internal fun backUpOnWifi(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(workDataOf(KEY_AUTOMATIC to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        // Queued behind one under way, never dropped for it: that one may itself be the run that found no Wi-Fi.
        WorkManager.getInstance(context).enqueueUniqueWork(BACKUP_ON_WIFI, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Restores the backup last opened, with the files chosen for it - see [com.lhacenmed.sona.feature.backup.BackupRepository.restore]. */
    fun restore(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(RESTORE, ExistingWorkPolicy.KEEP, userRequest<RestoreWorker>())
    }

    /**
     * The backup under way - an automatic one too, while it runs - or else the latest the user started, paused while it is,
     * or null before any.
     */
    fun backupRun(context: Context): Flow<BackupRun?> =
        combine(runOf(context, BACKUP), automaticRunOf(context), settingsOf(context).isBackupPaused) { run, automatic, isPaused ->
            when {
                run is BackupRun.Running -> run
                automatic != null -> automatic
                isPaused -> BackupRun.Paused
                else -> run
            }
        }

    /** An automatic backup while it runs - on its schedule, or waiting on Wi-Fi - or null while none does. */
    private fun automaticRunOf(context: Context): Flow<BackupRun.Running?> {
        val workManager = WorkManager.getInstance(context)
        return combine(
            workManager.getWorkInfosForUniqueWorkFlow(AUTOMATIC_BACKUP),
            workManager.getWorkInfosForUniqueWorkFlow(BACKUP_ON_WIFI),
        ) { scheduled, onWifi ->
            // Only running counts: between its runs, a scheduled backup waits as enqueued.
            (scheduled + onWifi).firstOrNull { it.state == WorkInfo.State.RUNNING }
                ?.let { BackupRun.Running(it.progress.toBackupProgress(), isAutomatic = true) }
        }
    }

    /** The latest restore, or null before any. */
    fun restoreRun(context: Context): Flow<BackupRun?> = runOf(context, RESTORE)

    private inline fun <reified W : ListenableWorker> userRequest() =
        OneTimeWorkRequestBuilder<W>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()

    private fun runOf(context: Context, name: String): Flow<BackupRun?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(name).map { infos ->
            val info = infos.lastOrNull() ?: return@map null
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> BackupRun.Running(info.progress.toBackupProgress())
                WorkInfo.State.SUCCEEDED ->
                    BackupRun.Succeeded(info.outputData.getString(KEY_OUTCOME)?.let { name -> BackupOutcome.entries.firstOrNull { it.name == name } })
                WorkInfo.State.CANCELLED -> BackupRun.Stopped
                WorkInfo.State.FAILED ->
                    BackupRun.Failed(
                        reason = info.outputData.getString(KEY_FAILURE)?.let { name -> BackupFailure.entries.firstOrNull { it.name == name } },
                        detail = info.outputData.getString(KEY_FAILURE_DETAIL),
                    )
            }
        }
}

private fun settingsOf(context: Context): BackupSettings =
    EntryPointAccessors.fromApplication(context, BackupEntryPoint::class.java).backupSettings()

internal fun BackupProgress.toData(): Data = Data.Builder()
    .putString(KEY_STEP, step.name)
    .putInt(KEY_DONE, done)
    .putInt(KEY_TOTAL, total)
    .apply {
        transfer?.let {
            putLong(KEY_BYTES_DONE, it.done)
            putLong(KEY_BYTES_TOTAL, it.total)
            putLong(KEY_BYTES_PER_SECOND, it.bytesPerSecond)
            putLong(KEY_BYTES_SENT, it.sent)
        }
    }
    .build()

private fun Data.toBackupProgress(): BackupProgress? {
    val step = BackupStep.entries.firstOrNull { it.name == getString(KEY_STEP) } ?: return null
    val transfer = if (KEY_BYTES_TOTAL in keyValueMap) {
        Transfer(getLong(KEY_BYTES_DONE, 0), getLong(KEY_BYTES_TOTAL, 0), getLong(KEY_BYTES_PER_SECOND, 0), getLong(KEY_BYTES_SENT, 0))
    } else {
        null
    }
    return BackupProgress(step, getInt(KEY_DONE, 0), getInt(KEY_TOTAL, 0), transfer)
}
