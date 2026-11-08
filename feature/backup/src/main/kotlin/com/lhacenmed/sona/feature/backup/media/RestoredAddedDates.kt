package com.lhacenmed.sona.feature.backup.media

import com.lhacenmed.sona.feature.backup.BackedUpMedia

/**
 * When each restored file counts as added on this device, by the path it now has: one second apart, in the order the
 * backup had them added - by [BackedUpMedia.addedAtSeconds], or when last changed for a backup made before that was
 * kept - the newest at [nowSeconds], and every one after [libraryNewestSeconds], the newest of the files already
 * here. So they stand newest in the library, in the order they were added where they were backed up.
 *
 * @param restored each file restored, and the path it now has.
 */
internal fun restoredAddedDates(restored: List<Pair<BackedUpMedia, String>>, libraryNewestSeconds: Long, nowSeconds: Long): Map<String, Long> {
    val ordered = restored.sortedBy { (media, _) -> media.addedAtSeconds.takeIf { it > 0 } ?: (media.modifiedAt / 1000) }
    val newest = maxOf(nowSeconds, libraryNewestSeconds + ordered.size)
    return ordered.withIndex().associate { (index, item) -> item.second to newest - (ordered.lastIndex - index) }
}
