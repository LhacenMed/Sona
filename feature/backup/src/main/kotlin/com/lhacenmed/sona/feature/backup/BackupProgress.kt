package com.lhacenmed.sona.feature.backup

import androidx.annotation.StringRes

/** Every step of a backup, then of a restore, in the order each runs - with what it says it is doing. */
enum class BackupStep(@StringRes val labelRes: Int) {
    CHECKING_FILES(R.string.backup_step_checking_files),
    READING_LIBRARY(R.string.backup_step_reading_library),
    CHECKING_LAST_BACKUP(R.string.backup_step_checking_last_backup),
    UPLOADING_FILES(R.string.backup_step_uploading_files),
    SAVING_ON_DEVICE(R.string.backup_step_saving_on_device),
    SAVING_TO_DRIVE(R.string.backup_step_saving_to_drive),
    CLEANING_DRIVE(R.string.backup_step_cleaning_drive),
    READING_BACKUP(R.string.restore_step_reading_backup),
    FINDING_FILES(R.string.restore_step_finding_files),
    RESTORING_LIBRARY(R.string.restore_step_restoring_library),
    DOWNLOADING_FILES(R.string.restore_step_downloading_files),
    ADDING_FILES(R.string.restore_step_adding_files),
}

/**
 * Bytes on their way to Drive: [done] of [total] there in all, going at [bytesPerSecond], and [sent] over the network
 * by this run - bytes sent again after a dropped connection included.
 */
data class Transfer(val done: Long, val total: Long, val bytesPerSecond: Long, val sent: Long)

/** Where a backup or a restore is: at [step], [done] of [total] files where it counts them, and its [transfer] while uploading. */
data class BackupProgress(val step: BackupStep, val done: Int = 0, val total: Int = 0, val transfer: Transfer? = null) {

    /** How far the step is, from 0 to 1 - by bytes while they move, else by files - or null where it cannot tell. */
    val fraction: Float?
        get() = when {
            transfer != null && transfer.total > 0 -> (transfer.done.toFloat() / transfer.total).coerceAtMost(1f)
            total > 0 -> done.toFloat() / total
            else -> null
        }
}
