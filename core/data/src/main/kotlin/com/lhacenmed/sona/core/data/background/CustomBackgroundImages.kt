package com.lhacenmed.sona.core.data.background

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lhacenmed.sona.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val DIRECTORY_NAME = "custom_background"

/**
 * The image the custom player background draws - the app's own copy of the one picked, so it is read without
 * a grant on someone else's file, and a backup can carry it to another device as it carries playlist covers.
 *
 * Copied as it is, at full size, since it fills the screen. Each copy is saved under a name of its own: a new
 * image is a new address, so no image cache can go on showing the one it replaced.
 */
@Singleton
class CustomBackgroundImages @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val directory = File(context.filesDir, DIRECTORY_NAME)

    /** Copies the image at [sourceUri] in, and returns the copy's address. */
    suspend fun import(sourceUri: String): String = withContext(ioDispatcher) {
        directory.mkdirs()
        val file = File(directory, UUID.randomUUID().toString())
        try {
            val input = context.contentResolver.openInputStream(Uri.parse(sourceUri)) ?: error("Could not read $sourceUri")
            input.use { file.outputStream().use(it::copyTo) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        Uri.fromFile(file).toString()
    }

    /**
     * Lets go of [uri]: deletes it where it is one of these copies, else releases the read grant an image picked
     * before copies were kept was held by.
     */
    suspend fun release(uri: String) = withContext(ioDispatcher) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            parsed.path?.let(::File)?.takeIf { it.parentFile == directory }?.delete()
        } else {
            runCatching { context.contentResolver.releasePersistableUriPermission(parsed, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }
}
