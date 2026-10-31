package com.lhacenmed.sona.feature.backup

import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val BACKUP_EXTENSION = "sona"

/** What a backup made at [createdAt] is called, wherever it is kept: `sona-20261030-120000.sona`. */
internal fun backupFileName(createdAt: Long): String =
    "sona-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(createdAt))}.$BACKUP_EXTENSION"

/**
 * A backup kept at a [BackupDestination], by the id that destination knows it by. Its [name] is the same wherever it
 * is kept - see [backupFileName] - which is how one backup kept in two places is known to be one.
 */
data class StoredBackup(
    val id: String,
    val name: String,
    val createdAt: Long,
    val sizeBytes: Long,
)

/**
 * Somewhere backups are kept - this device, or the user's Google Drive. Each holds backups only; how many are
 * kept, and what goes in one, is decided once for all of them by [BackupRepository].
 */
interface BackupDestination {

    /** Every backup kept here, the latest first. */
    suspend fun list(): List<StoredBackup>

    /** Keeps the backup [write] writes, as [name] - whole, or not at all - and returns its [checksumOf]. */
    suspend fun add(name: String, write: (OutputStream) -> Unit): String

    /**
     * The SHA-256 of [backup]'s bytes as kept here now - for telling it is still what was written - or null where
     * this cannot tell.
     */
    suspend fun checksumOf(backup: StoredBackup): String?

    suspend fun open(backup: StoredBackup): InputStream

    suspend fun delete(backup: StoredBackup)
}
