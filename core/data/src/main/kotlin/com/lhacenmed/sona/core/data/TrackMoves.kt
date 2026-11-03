package com.lhacenmed.sona.core.data

import kotlin.math.abs

/** How far two readings of one file's length may differ: MediaStore and a file read off the disk round it differently. */
private const val DURATION_TOLERANCE_MS = 1_000L

/** A track's file, as [movedTracks] tells a moved one from a new one. */
internal data class TrackFile(
    val id: Long,
    val path: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val isVideo: Boolean,
    val title: String,
    val artist: String,
)

/**
 * Which of the files [found] - new to the library - are files [gone] from it, moved or renamed. Nothing is read to
 * tell, and this runs on every scan, for every file; only what left and arrived within moments of each other is
 * ever compared, so a few clues are enough. From the surest down, each gone file paired once:
 * 1. of the very same size and length, and the same name, so two copies of one file stay two tracks;
 * 2. of the very same size and length;
 * 3. of the same length and name - its tags changed on the way, and with them its size;
 * 4. of the same length and tags - its size alone changed;
 * 5. of a length only they share among what left and arrived - both its name and its tags changed.
 *
 * The found files' paths to the gone files they are.
 */
internal fun movedTracks(gone: List<TrackFile>, found: List<TrackFile>): Map<String, TrackFile> {
    val moves = LinkedHashMap<String, TrackFile>()
    val left = gone.toMutableList()
    fun pairWhere(isAlike: (TrackFile, TrackFile) -> Boolean) {
        for (file in found) {
            if (file.path in moves) continue
            val moved = left.firstOrNull { isAlike(it, file) } ?: continue
            moves[file.path] = moved
            left -= moved
        }
    }
    pairWhere { goneFile, file -> goneFile.hasBytesOf(file) && goneFile.name == file.name }
    pairWhere(TrackFile::hasBytesOf)
    pairWhere { goneFile, file -> goneFile.hasLengthOf(file) && goneFile.name == file.name }
    pairWhere { goneFile, file -> goneFile.hasLengthOf(file) && goneFile.title == file.title && goneFile.artist == file.artist }
    val waiting = found.filter { it.path !in moves }
    pairWhere { goneFile, file -> goneFile.hasLengthOf(file) && left.count { it.hasLengthOf(file) } == 1 && waiting.count { it.hasLengthOf(goneFile) } == 1 }
    return moves
}

/**
 * Which of the files [existing] in the library are also files [gone] from it: a move done as a copy and then a delete,
 * which the library sees as a new file, and only later as the old one leaving - or one of two copies deleted. Either
 * way one file of those bytes is left, and it is that track. Only the very same size and length will do. Their paths
 * to the gone files they are.
 */
internal fun copiesOfGoneTracks(gone: List<TrackFile>, existing: List<TrackFile>): Map<String, TrackFile> {
    val copies = LinkedHashMap<String, TrackFile>()
    for (goneFile in gone) {
        val alike = existing.filter { it.path !in copies && goneFile.hasBytesOf(it) }
        val copy = alike.firstOrNull { it.name == goneFile.name } ?: alike.firstOrNull() ?: continue
        copies[copy.path] = goneFile
    }
    return copies
}

private fun TrackFile.hasBytesOf(other: TrackFile): Boolean = sizeBytes > 0 && sizeBytes == other.sizeBytes && hasLengthOf(other)

private fun TrackFile.hasLengthOf(other: TrackFile): Boolean =
    isVideo == other.isVideo && durationMs > 0 && abs(durationMs - other.durationMs) <= DURATION_TOLERANCE_MS

private val TrackFile.name get() = path.substringAfterLast('/')
