package com.lhacenmed.sona.core.vault

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.lhacenmed.sona.core.vault.data.VaultItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

/** The folder in shared storage a file moved out of the vault lands in, under Music or Movies. */
private const val MOVED_OUT_FOLDER = "Sona"

/**
 * The vault's files: plain media files under its own directory, under random names so they read as nothing
 * in particular - played straight from where they lie, with nothing to decrypt or copy first.
 */
internal class VaultFiles @Inject constructor(@ApplicationContext private val context: Context) {

    private val mediaDirectory: File get() = File(context.vaultDirectory, "media").apply { mkdirs() }

    fun fileOf(item: VaultItem): File = File(mediaDirectory, item.fileName)

    /** Copies [source] in under a new name keeping [extension], and returns that name. Nothing is left of a copy that fails. */
    fun import(source: InputStream, extension: String): String {
        val fileName = UUID.randomUUID().toString() + extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        val file = File(mediaDirectory, fileName)
        try {
            file.outputStream().use { source.copyTo(it) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return fileName
    }

    fun delete(item: VaultItem) {
        fileOf(item).delete()
    }

    /**
     * Copies [item] back into shared storage - Music/Sona or Movies/Sona - where the library finds it again.
     * Through MediaStore from Android 10, which needs no permission; below it, straight into the folder, with
     * the storage permission.
     */
    fun export(item: VaultItem) {
        val source = fileOf(item)
        val publicDirectory = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = if (item.isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val pending = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalFileName)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$publicDirectory/$MOVED_OUT_FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(collection, pending)) { "MediaStore refused ${item.originalFileName}" }
            try {
                checkNotNull(resolver.openOutputStream(uri)).use { output -> source.inputStream().use { it.copyTo(output) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = File(Environment.getExternalStoragePublicDirectory(publicDirectory), MOVED_OUT_FOLDER).apply { mkdirs() }
            val target = freeFileIn(directory, item.originalFileName)
            source.copyTo(target)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), null, null)
        }
    }

    /** [name] in [directory], or "name (2).ext" and on, as MediaStore itself names a clash. */
    private fun freeFileIn(directory: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        return generateSequence(1) { it + 1 }
            .map { copy -> File(directory, if (copy == 1) name else "$base ($copy)$extension") }
            .first { !it.exists() }
    }
}
