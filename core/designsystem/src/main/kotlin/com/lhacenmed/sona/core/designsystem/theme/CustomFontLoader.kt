package com.lhacenmed.sona.core.designsystem.theme

import android.content.Context
import android.graphics.Typeface as AndroidTypeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A `.ttf` the user picked, made into a font family. ArchiveTune's `CustomFontLoader`.
 *
 * The file is copied into the app's own storage before it is read, because a typeface is made from a
 * file path and a picked document is only a uri.
 */
object CustomFontLoader {
    /** What the picker offers: the types a `.ttf` goes by, and the catch-all some file managers give it. */
    val supportedMimeTypes = arrayOf(
        "font/ttf",
        "application/x-font-ttf",
        "application/x-font-truetype",
        "application/octet-stream",
    )

    fun displayName(context: Context, uri: Uri): String {
        val resolvedName = runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()

        return resolvedName?.trim()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('?')?.trim()?.takeIf { it.isNotBlank() }
            ?: uri.toString()
    }

    fun isSupportedTtf(context: Context, uri: Uri): Boolean = displayName(context, uri).endsWith(".ttf", ignoreCase = true)

    /** The font at [uriString], or null when it is gone or is not a font. */
    suspend fun loadFontFamily(context: Context, uriString: String): FontFamily? =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                if (!isSupportedTtf(context, uri)) return@withContext null
                FontFamily(Typeface(AndroidTypeface.createFromFile(copyToPrivateFontFile(context, uri, uriString))))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    private fun copyToPrivateFontFile(context: Context, uri: Uri, uriString: String): File {
        val fontDirectory = File(context.filesDir, "custom_fonts").apply { mkdirs() }
        val fontFile = File(fontDirectory, "${Integer.toHexString(uriString.hashCode())}.ttf")
        context.contentResolver.openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream)
            fontFile.outputStream().use { inputStream.copyTo(it) }
        }
        return fontFile
    }
}
