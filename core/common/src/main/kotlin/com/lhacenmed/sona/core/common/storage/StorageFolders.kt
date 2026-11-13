package com.lhacenmed.sona.core.common.storage

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/** Where Android's folder picker's folders live: the device's storage, by volume - "primary" its own, a memory card by its id. */
private const val ExternalStorageAuthority = "com.android.externalstorage.documents"

/**
 * The folder [tree] - one Android's folder picker gave - as folders are kept: within the device's own storage, its path in it
 * ("Music/Sona"); on a memory card, its whole path. Null for one not on the device's storage.
 */
fun storageFolderPathOf(tree: Uri): String? {
    if (tree.authority != ExternalStorageAuthority) return null
    val (volume, inVolume) = DocumentsContract.getTreeDocumentId(tree).split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return if (volume.equals("primary", ignoreCase = true)) inVolume.trim('/') else File("/storage/$volume", inVolume).path
}

/** The folder at [path], as folders are kept - see [storageFolderPathOf]: on the device's own storage, unless whole. */
fun storageFolderAt(path: String): File = if (path.startsWith("/")) File(path) else File(Environment.getExternalStorageDirectory(), path)
