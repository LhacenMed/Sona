package com.lhacenmed.sona.open

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.IntentCompat

/** A track or a video another app asked Sona to play - see [OpenMediaActivity] - by its address and its name. */
internal class OpenedMedia(val uri: Uri, val title: String, val isVideo: Boolean)

/**
 * What [intent] asks to be played: the file a file manager's "Open with" names, or the one a share sends. Its title is
 * its file's name until its tags are read; it is a video by the type the asking app gives it, or its own address does.
 * Null for an intent with no file in it.
 */
internal fun Context.openedMediaOf(intent: Intent): OpenedMedia? {
    val uri = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    } ?: return null
    val name = displayNameOf(uri)
    val type = intent.type?.takeUnless { it == "*/*" }
        ?: runCatching { contentResolver.getType(uri) }.getOrNull()
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
    return OpenedMedia(uri, title = name.substringBeforeLast('.').ifBlank { name }, isVideo = type?.startsWith("video/") == true)
}

/** The name the file goes by where it is kept - or, where its keeper gives none, the end of its address. */
private fun Context.displayNameOf(uri: Uri): String {
    val shownName = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return shownName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
}
