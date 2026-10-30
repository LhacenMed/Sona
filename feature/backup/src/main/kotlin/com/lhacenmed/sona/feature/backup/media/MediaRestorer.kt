package com.lhacenmed.sona.feature.backup.media

import com.lhacenmed.sona.feature.backup.BackedUpMedia
import java.io.File
import java.io.IOException

/** A storage volume's root, and a path's place under it: `/storage/emulated/0/Music/a.mp3`, `/storage/1A2B-3C4D/a.mp3`. */
private val VOLUME_PATH = Regex("^(/storage/emulated/\\d+|/storage/[^/]+)/(.+)$")

/**
 * Brings files a device is missing back from the [cloud], never over a file already there.
 *
 * Each is downloaded into [incoming] - inside the backup folder, whose `.nomedia` keeps a half-written file out
 * of every library - checked against its fingerprint, and only then moved to its place: where it was, or on
 * [primaryStorage] when the volume it was on is not in this device. A different file at that place keeps it,
 * and the restored one takes the next free name, `a (1).mp3`. Restoring a file that is already in place, as a
 * run cut short and started again does, finds it there and leaves it.
 */
class MediaRestorer(
    private val cloud: CloudMediaStore,
    private val incoming: File,
    private val primaryStorage: File,
) {

    /** Brings [media] back, and returns where it now is. */
    suspend fun restore(media: BackedUpMedia): File {
        val placement = placementOf(media)
        if (placement.exists()) return placement
        incoming.mkdirs()
        val download = File(incoming, media.fingerprint)
        try {
            cloud.download(media.fingerprint, download)
            if (fingerprintOf(download) != media.fingerprint) throw IOException("${media.path} did not download intact")
            placement.parentFile?.mkdirs()
            if (!download.renameTo(placement)) {
                // Another volume: copied beside its place under a hidden name, so it only appears there whole.
                val partial = File(placement.parentFile, ".${placement.name}.part")
                download.copyTo(partial, overwrite = true)
                if (!partial.renameTo(placement)) throw IOException("${media.path} could not be put in place")
            }
        } finally {
            download.delete()
        }
        placement.setLastModified(media.modifiedAt)
        return placement
    }

    /** Where [media] goes: its place, or the file already holding its bytes there or under a numbered name. */
    internal fun placementOf(media: BackedUpMedia): File {
        val volumePath = VOLUME_PATH.matchEntire(media.path)?.destructured
        val original = when {
            volumePath == null -> File(media.path)
            File(volumePath.component1()).isDirectory -> File(media.path)
            else -> File(primaryStorage, volumePath.component2())
        }
        val name = original.nameWithoutExtension
        val extension = original.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        return generateSequence(0) { it + 1 }
            .map { copy -> if (copy == 0) original else File(original.parentFile, "$name ($copy)$extension") }
            .first { !it.exists() || fingerprintOf(it) == media.fingerprint }
    }
}
