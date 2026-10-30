package com.lhacenmed.sona.feature.backup

import android.content.Context
import com.lhacenmed.sona.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val FOLDER_NAME = "Backup"

/**
 * The backups kept on this device: in Sona's own folder under `Android/media`, which any file manager reaches
 * and which needs no permission - the way WhatsApp keeps its own.
 *
 * Android empties that folder when Sona is uninstalled, so these undo a change gone wrong rather than survive a
 * lost device; that is what Google Drive is for. Its `.nomedia` keeps it, and the files a restore brings back
 * while they download, out of every library.
 */
@Singleton
class LocalBackups @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackupDestination {

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

    override suspend fun list(): List<StoredBackup> = withContext(ioDispatcher) {
        directory.listFiles { file -> file.isFile && file.extension == BACKUP_EXTENSION }.orEmpty()
            .map { StoredBackup(it.path, it.name, it.lastModified(), it.length()) }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun add(name: String, write: (OutputStream) -> Unit) = withContext(ioDispatcher) {
        val partial = File(directory, ".$name.part")
        try {
            partial.outputStream().buffered().use(write)
            check(partial.renameTo(File(directory, name))) { "Could not keep $name" }
        } finally {
            partial.delete()
        }
    }

    override suspend fun open(backup: StoredBackup): InputStream = withContext(ioDispatcher) { File(backup.id).inputStream() }

    override suspend fun delete(backup: StoredBackup) {
        withContext(ioDispatcher) { File(backup.id).delete() }
    }
}
