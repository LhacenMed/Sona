package com.lhacenmed.sona.feature.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val FOLDER_NAME = "Backup"
private const val EXTENSION = "sona"
private const val KEPT_BACKUPS = 5

/** A backup kept on this device. */
data class LocalBackup(
    val file: File,
    val createdAt: Long,
    val sizeBytes: Long,
)

/**
 * The backups kept on this device, the last [KEPT_BACKUPS] of them: in Sona's own folder under `Android/media`,
 * which any file manager reaches and which needs no permission - the way WhatsApp keeps its own.
 *
 * Android empties that folder when Sona is uninstalled, so these undo a change gone wrong rather than survive a
 * lost device; that is what a cloud backup is for. Its `.nomedia` keeps it, and the files a restore brings back
 * while they download, out of every library.
 */
@Singleton
class LocalBackups @Inject constructor(@ApplicationContext private val context: Context) {

    /** The backup folder, made - with its `.nomedia` - the first time it is needed. */
    val directory: File
        get() {
            @Suppress("DEPRECATION") // Android/media/<package> is still where an app keeps files the user can reach.
            val mediaDirectory = context.externalMediaDirs.firstOrNull() ?: error("Shared storage is not available")
            return File(mediaDirectory, FOLDER_NAME).apply {
                mkdirs()
                File(this, ".nomedia").createNewFile()
            }
        }

    /** Every backup kept, the latest first. */
    fun list(): List<LocalBackup> =
        directory.listFiles { file -> file.isFile && file.extension == EXTENSION }.orEmpty()
            .map { LocalBackup(it, it.lastModified(), it.length()) }
            .sortedByDescending { it.createdAt }

    /** Adds the backup [write] writes - whole, or not at all - and lets go of the oldest beyond [KEPT_BACKUPS]. */
    fun add(createdAt: Long, write: (OutputStream) -> Unit) {
        val name = "sona-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(createdAt))}.$EXTENSION"
        val partial = File(directory, ".$name.part")
        try {
            partial.outputStream().buffered().use(write)
            check(partial.renameTo(File(directory, name))) { "Could not keep $name" }
        } finally {
            partial.delete()
        }
        list().drop(KEPT_BACKUPS).forEach { it.file.delete() }
    }
}
