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
 * The file is copied into the app's own storage the first time it is read, because a typeface is made from
 * a file path and a picked document is only a uri. Later launches read that copy, which is quick and does
 * not need the picked document to still be there.
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

    /** What a picked font is used for, each keeping a copy of its own: the app's text, or a video's subtitles. */
    enum class Use(internal val prefix: String) {
        APP(""),
        SUBTITLES("subtitles-"),
        ;

        internal fun keeps(file: File): Boolean = file.name.startsWith(SUBTITLES.prefix) == (this == SUBTITLES)
    }

    /** The font at [uriString], or null when it is gone or is not a font. */
    suspend fun loadFontFamily(context: Context, uriString: String): FontFamily? =
        loadTypeface(context, uriString, Use.APP)?.let { FontFamily(Typeface(it)) }

    /** The font at [uriString] as a typeface, kept for [use] - or null when it is gone or is not a font. */
    suspend fun loadTypeface(context: Context, uriString: String, use: Use): AndroidTypeface? =
        withContext(Dispatchers.IO) {
            try {
                val fontFile = File(fontDirectory(context), "${use.prefix}${Integer.toHexString(uriString.hashCode())}.ttf")
                if (!fontFile.exists()) {
                    val uri = Uri.parse(uriString)
                    if (!isSupportedTtf(context, uri)) return@withContext null
                    copyToPrivateFontFile(context, uri, fontFile, use)
                }
                AndroidTypeface.createFromFile(fontFile)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    /** Where the copies of the fonts in use are kept - which a backup carries, so a restored font needs no picking again. */
    fun fontDirectory(context: Context): File = File(context.filesDir, "custom_fonts")

    /**
     * Copies the font at [uri] to [fontFile], in place of any font copied before for [use] - only the font in
     * use is kept. Written beside it and then renamed, so an interrupted copy never leaves a broken font to be read.
     */
    private fun copyToPrivateFontFile(context: Context, uri: Uri, fontFile: File, use: Use) {
        val fontDirectory = fontDirectory(context)
        fontDirectory.mkdirs()
        fontDirectory.listFiles()?.filter(use::keeps)?.forEach(File::delete)
        val partialFile = File(fontDirectory, "${fontFile.name}.partial")
        context.contentResolver.openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream)
            partialFile.outputStream().use { inputStream.copyTo(it) }
        }
        check(partialFile.renameTo(fontFile))
    }
}
