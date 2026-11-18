package com.lhacenmed.sona.feature.backup

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val BACKUP_EXTENSION = "sona"

/**
 * What a backup file is to Android - bytes of a kind it has no name for, as every `.sona` file is - so a file picker offers
 * these and not the photos, music and documents around them.
 */
val BackupFileTypes = arrayOf("application/octet-stream")

/** Whether [fileName] is a backup's, by its extension - the one thing that tells a backup apart among plain bytes. */
fun isBackupFileName(fileName: String): Boolean = fileName.endsWith(".$BACKUP_EXTENSION", ignoreCase = true)

/** This device, as a backup made on it says - see [BackupDevice]. */
internal fun thisDevice(context: Context): BackupDevice {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.titlecase() }
    // A model is often named with its maker already - "Pixel 8" is Google's, "SM-S918B" is not named Samsung's.
    val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true) || manufacturer == "Google") Build.MODEL else "$manufacturer ${Build.MODEL}"
    val name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME).orEmpty()
    return BackupDevice(name = name.takeUnless { it == Build.MODEL || it == model }.orEmpty(), model = model)
}

/** What a backup made at [createdAt] is called, wherever it is kept: `sona-20261030-120000.sona`. */
internal fun backupFileName(createdAt: Long): String =
    "sona-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(createdAt))}.$BACKUP_EXTENSION"

/**
 * A backup kept at a [BackupDestination], by the id that destination knows it by. Its [name] is the same wherever it
 * is kept - see [backupFileName] - which is how one backup kept in two places is known to be one. The [device] it was made
 * on, where the destination keeps it beside the backup - Drive does, for its list to say without reading each.
 */
data class StoredBackup(
    val id: String,
    val name: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val device: BackupDevice? = null,
)

/**
 * Somewhere backups are kept - this device, or the user's Google Drive. Each holds backups only; how many are
 * kept, and what goes in one, is decided once for all of them by [BackupRepository].
 */
interface BackupDestination {

    /** Every backup kept here, the latest first. */
    suspend fun list(): List<StoredBackup>

    /**
     * Keeps the backup [write] writes, as [name], made on [device] - whole, or not at all - and returns its [checksumOf].
     */
    suspend fun add(name: String, device: BackupDevice, write: (OutputStream) -> Unit): String

    /**
     * The SHA-256 of [backup]'s bytes as kept here now - for telling it is still what was written - or null where
     * this cannot tell.
     */
    suspend fun checksumOf(backup: StoredBackup): String?

    suspend fun open(backup: StoredBackup): InputStream

    suspend fun delete(backup: StoredBackup)
}
