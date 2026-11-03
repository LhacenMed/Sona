package com.lhacenmed.sona.feature.backup.media

import com.lhacenmed.sona.feature.backup.BackedUpMedia
import kotlin.math.abs

/** How far two readings of one file's length may differ. */
private const val DURATION_TOLERANCE_MS = 1_000L

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

/** A file of this device's library, as a backup's files are compared with it. */
data class LibraryFile(val path: String, val durationMs: Long)

/**
 * Compares a backup's [media] with this device's files, changing nothing. A file that changed since the backup - its
 * tags corrected, say - is still that file, and is left as it is: a restore never adds an older copy beside it.
 *
 * For each backed-up file the first that holds decides:
 * 1. there is a file at its place - present there, whatever its bytes are now;
 * 2. a file elsewhere had its fingerprint - present at that one, so a backup listing the same bytes twice is
 *    one file on this device;
 * 3. a library file of the same size has its fingerprint - present at that one, wherever it was moved;
 * 4. a library file no other backed-up file has for its place has its name and its length - present at that one,
 *    moved and changed since;
 * 5. the cloud keeps it - missing;
 * 6. otherwise - unavailable.
 *
 * Only files of exactly the backed-up size are ever fingerprinted, and [fingerprintOf] reads each file once.
 *
 * @param libraryFiles this device's library files.
 * @param placeOf where a backed-up path puts a file on this device - see [MediaRestorer.placeOf].
 * @param sizeOf the size of the file at a path, or null where there is none.
 * @param fingerprintOf the fingerprint of the file at a path, or null where there is none.
 */
suspend fun reconcileMedia(
    media: List<BackedUpMedia>,
    libraryFiles: List<LibraryFile>,
    placeOf: (String) -> String,
    sizeOf: (String) -> Long?,
    fingerprintOf: suspend (String) -> String?,
    cloudFingerprints: Set<String>,
): MediaPlan {
    val librarySizes = libraryFiles.groupBy { sizeOf(it.path) }
    val backedUpPlaces = media.mapTo(HashSet()) { placeOf(it.path) }
    val foundAt = HashMap<String, String>()
    val present = LinkedHashMap<String, String>()
    val missing = LinkedHashMap<String, MutableList<BackedUpMedia>>()
    val unavailable = mutableListOf<BackedUpMedia>()

    suspend fun BackedUpMedia.isAt(path: String) = sizeOf(path) == sizeBytes && fingerprintOf(path) == fingerprint

    fun BackedUpMedia.isMovedAndChangedTo(file: LibraryFile) =
        file.path !in backedUpPlaces && durationMs > 0 && abs(durationMs - file.durationMs) <= DURATION_TOLERANCE_MS &&
            file.path.substringAfterLast('/') == path.substringAfterLast('/')

    for (item in media) {
        val place = placeOf(item.path)
        val devicePath = when {
            sizeOf(place) != null -> place
            else -> foundAt[item.fingerprint]
                ?: librarySizes[item.sizeBytes].orEmpty().firstOrNull { item.isAt(it.path) }?.path
                ?: libraryFiles.firstOrNull { item.isMovedAndChangedTo(it) }?.path
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
