package com.lhacenmed.sona.core.common.storage

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Resolves a Storage Access Framework [Uri] to a real filesystem path.
 *
 * The library is indexed by path, so anything the user picks through a system picker has to come
 * back to one before it can be matched against a track.
 *
 * This only handles the common cases - primary external storage and simply-named secondary volumes,
 * whose document ids are shaped like `volume:relative/path`. That is an intentionally scoped
 * limitation rather than a full storage-agnostic resolver: a file picked from a cloud provider has
 * no path on this device, and so has no track to match either.
 */
fun documentPathOrNull(treeUri: Uri, isTree: Boolean): String? {
    val documentId = runCatching {
        if (isTree) DocumentsContract.getTreeDocumentId(treeUri) else DocumentsContract.getDocumentId(treeUri)
    }.getOrNull() ?: return null

    val parts = documentId.split(":")
    if (parts.size != 2) return null

    val volume = parts[0]
    val relativePath = parts[1]
    val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
    return if (relativePath.isEmpty()) root else "$root/$relativePath"
}
