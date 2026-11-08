package com.lhacenmed.sona.feature.backup.media

import com.lhacenmed.sona.core.data.fingerprint.fingerprintOf
import com.lhacenmed.sona.feature.backup.BackedUpMedia
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A storage volume's root, and a path's place under it: `/storage/emulated/0/Music/a.mp3`, `/storage/1A2B-3C4D/a.mp3`. */
private val VOLUME_PATH = Regex("^(/storage/emulated/\\d+|/storage/[^/]+)/(.+)$")

/**
 * Brings files a device is missing back from the [cloud], never over a file already there.
 *
 * Each is downloaded into [incoming] - inside the backup folder, whose `.nomedia` keeps a half-written file out
 * of every library - checked against its fingerprint as it arrives, and only then moved to its place: where it was, or on
 * [primaryStorage] when the volume it was on is not in this device. A different file at that place keeps it,
 * and the restored one takes the next free name, `a (1).mp3`. Restoring a file that is already in place, as a
 * run cut short and started again does, finds it there and leaves it.
 */
class MediaRestorer(
    private val cloud: CloudMediaStore,
    private val incoming: File,
    private val primaryStorage: File,
) {

    /** Placing is one file at a time, so two downloads never take the same free name. */
    private val placing = Mutex()

    /** Brings [media] back, and returns where it now is. Several can be brought back at once. */
    suspend fun restore(media: BackedUpMedia): File {
        placementOf(media).takeIf { it.exists() }?.let { return it }
        incoming.mkdirs()
        val download = File(incoming, media.fingerprint)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(cloud.open(media.fingerprint), digest).use { input -> download.outputStream().use { input.copyTo(it) } }
            if (digest.digest().joinToString("") { "%02x".format(it) } != media.fingerprint) {
                throw IOException("${media.path} did not download intact")
            }
            return placing.withLock { place(download, media) }
        } finally {
            download.delete()
        }
    }

    /** Moves [download] to where [media] goes - unless the same bytes got there meanwhile. */
    private fun place(download: File, media: BackedUpMedia): File {
        val placement = placementOf(media)
        if (placement.exists()) return placement
        placement.parentFile?.mkdirs()
        if (!download.renameTo(placement)) {
            // Another volume: copied beside its place under a hidden name, so it only appears there whole.
            val partial = File(placement.parentFile, ".${placement.name}.part")
            download.copyTo(partial, overwrite = true)
            if (!partial.renameTo(placement)) throw IOException("${media.path} could not be put in place")
        }
        placement.setLastModified(media.modifiedAt)
        return placement
    }

    /** Where the file backed up at [path] belongs on this device: there, or on [primaryStorage] where its volume is not here. */
    fun placeOf(path: String): String {
        val volumePath = VOLUME_PATH.matchEntire(path)?.destructured ?: return path
        return if (File(volumePath.component1()).isDirectory) path else File(primaryStorage, volumePath.component2()).path
    }

    /** Where [media] goes: its place, or the file already holding its bytes there or under a numbered name. */
    internal fun placementOf(media: BackedUpMedia): File {
        val original = File(placeOf(media.path))
        val name = original.nameWithoutExtension
        val extension = original.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        return generateSequence(0) { it + 1 }
            .map { copy -> if (copy == 0) original else File(original.parentFile, "$name ($copy)$extension") }
            .first { !it.exists() || fingerprintOf(it) == media.fingerprint }
    }
}
