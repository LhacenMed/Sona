package com.lhacenmed.sona.core.data

/** A track's file, as [movedTracks] tells a moved one from a new one. */
internal data class TrackFile(val id: Long, val path: String, val sizeBytes: Long, val isVideo: Boolean)

/**
 * Which of the files [found] - new to the library - are files [gone] from it, moved or renamed: the same kind, the
 * same size, and the same bytes, [fingerprintOf] a found one being what [lastFingerprintOf] a gone one was. Only
 * a gone file of a found one's size is ever compared, so a found file is read only where one might match.
 *
 * Each gone file is paired once - with the found file of its own name first, where several alike are found - so
 * two copies of one file stay two tracks. The gone tracks' ids to the ids of those they are now.
 */
internal suspend fun movedTracks(
    gone: List<TrackFile>,
    found: List<TrackFile>,
    lastFingerprintOf: suspend (TrackFile) -> String?,
    fingerprintOf: suspend (TrackFile) -> String?,
): Map<Long, Long> {
    val goneByKind = gone.groupBy { it.sizeBytes to it.isVideo }
    val goneFingerprints = HashMap<Long, String?>()
    val moves = LinkedHashMap<Long, Long>()
    // Files keeping their names are paired first, so a renamed copy never takes another's track.
    for (file in found.sortedByDescending { candidate -> gone.any { it.name == candidate.name } }) {
        val candidates = goneByKind[file.sizeBytes to file.isVideo].orEmpty().filter { it.id !in moves }
        if (candidates.isEmpty()) continue
        val fingerprint = fingerprintOf(file) ?: continue
        val alike = candidates.filter { goneFingerprints.getOrPut(it.id) { lastFingerprintOf(it) } == fingerprint }
        val moved = alike.firstOrNull { it.name == file.name } ?: alike.firstOrNull() ?: continue
        moves[moved.id] = file.id
    }
    return moves
}

private val TrackFile.name get() = path.substringAfterLast('/')
