package com.lhacenmed.sona.feature.backup.media

import com.lhacenmed.sona.feature.backup.BackedUpMedia

/**
 * How a backup's files stand against this device.
 *
 * @property present each backed-up path whose file this device has, to the path it has it at.
 * @property missing the files this device lacks that the cloud keeps, so a restore can bring them back.
 * @property unavailable the files this device lacks and the cloud does not keep.
 */
data class MediaPlan(
    val present: Map<String, String>,
    val missing: List<MissingMedia>,
    val unavailable: List<BackedUpMedia>,
)

/** A file to bring back: [media], and [otherPaths] - the paths the backup had the same bytes at besides its own. */
data class MissingMedia(
    val media: BackedUpMedia,
    val otherPaths: List<String>,
)

/**
 * Compares a backup's [media] with this device's files, changing nothing. Files are compared by fingerprint
 * alone - by their bytes - never by their names or tags, so two different files are never taken for one.
 *
 * For each backed-up file the first that holds decides:
 * 1. the file at its path has its fingerprint - present there;
 * 2. a file elsewhere had its fingerprint - present at that one, so a backup listing the same bytes twice is
 *    one file on this device;
 * 3. a library file of the same size has its fingerprint - present at that one, wherever it was moved;
 * 4. the cloud keeps it - missing;
 * 5. otherwise - unavailable.
 *
 * Only files of exactly the backed-up size are ever fingerprinted, and [fingerprintOf] reads each file once.
 *
 * @param libraryPaths the paths of this device's library files.
 * @param sizeOf the size of the file at a path, or null where there is none.
 * @param fingerprintOf the fingerprint of the file at a path, or null where there is none.
 */
suspend fun reconcileMedia(
    media: List<BackedUpMedia>,
    libraryPaths: List<String>,
    sizeOf: (String) -> Long?,
    fingerprintOf: suspend (String) -> String?,
    cloudFingerprints: Set<String>,
): MediaPlan {
    val librarySizes = libraryPaths.groupBy { sizeOf(it) }
    val foundAt = HashMap<String, String>()
    val present = LinkedHashMap<String, String>()
    val missing = LinkedHashMap<String, MutableList<BackedUpMedia>>()
    val unavailable = mutableListOf<BackedUpMedia>()

    suspend fun BackedUpMedia.isAt(path: String) = sizeOf(path) == sizeBytes && fingerprintOf(path) == fingerprint

    for (item in media) {
        val devicePath = when {
            item.isAt(item.path) -> item.path
            else -> foundAt[item.fingerprint]
                ?: librarySizes[item.sizeBytes].orEmpty().firstOrNull { it != item.path && item.isAt(it) }
        }
        when {
            devicePath != null -> {
                foundAt.putIfAbsent(item.fingerprint, devicePath)
                present[item.path] = devicePath
            }
            item.fingerprint in missing -> missing.getValue(item.fingerprint) += item
            item.fingerprint in cloudFingerprints -> missing[item.fingerprint] = mutableListOf(item)
            else -> unavailable += item
        }
    }
    return MediaPlan(
        present = present,
        missing = missing.values.map { copies -> MissingMedia(copies.first(), copies.drop(1).map { it.path }) },
        unavailable = unavailable,
    )
}
