package com.lhacenmed.sona.core.downloader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Each download's own folder, where everything it fetches waits until it is saved - YTDLnis's: what is fetched outlives the
 * download being paused, failing, or the app being stopped or killed, and is carried on from its last byte. On the app's own
 * external storage, which Android never clears to free space as it does its cache.
 *
 * A folder is kept while its download may carry on - waiting, paused, failed - and deleted once it is saved or cancelled.
 */
@Singleton
class DownloadWorkspaces @Inject constructor(@ApplicationContext private val context: Context) {
    private val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")

    private companion object {
        const val PROGRESS = "progress"
    }

    /** [id]'s folder, made where it is not yet. */
    fun of(id: Long): File = File(root, id.toString()).apply { mkdirs() }

    /** Keeps how far [id] had got - [downloadedBytes] of [totalBytes] of the file it was on - as it is paused. */
    fun saveProgress(id: Long, downloadedBytes: Long?, totalBytes: Long?) {
        File(of(id), PROGRESS).writeText("${downloadedBytes ?: ""} ${totalBytes ?: ""}")
    }

    /** How far [id] had got as it was paused - see [saveProgress] - bytes downloaded and of how many; null where it was not. */
    fun progressOf(id: Long): Pair<Long?, Long?>? =
        File(File(root, id.toString()), PROGRESS).takeIf(File::isFile)?.readText()?.split(' ')?.let { it[0].toLongOrNull() to it.getOrNull(1)?.toLongOrNull() }

    /**
     * The parts [id] is fetched into, as it is played while it downloads - read back from its folder, for one paused before the
     * app last stopped: the file fetched directly, or each stream yt-dlp fetches - whole, or growing still in its `.part` - in
     * the order it fetched them. None for one not begun.
     */
    fun partsOf(id: Long): List<DownloadEvent.Part> {
        val folder = File(root, id.toString())
        File(folder, DirectPartName).takeIf(File::isFile)?.let { return listOf(DownloadEvent.Part(it, it)) }
        return folder.listFiles().orEmpty()
            .map { if (it.name.endsWith(".part")) File(it.path.removeSuffix(".part")) else it }
            .filter { it.isStream }
            .distinct()
            .sortedBy { (File(it.path + ".part").takeIf(File::exists) ?: it).lastModified() }
            .map { DownloadEvent.Part(File(it.path + ".part"), it) }
    }

    /** Deletes the folders of [ids], with all they hold. */
    fun discard(ids: Collection<Long>) = ids.forEach { File(root, it.toString()).deleteRecursively() }

    /** Deletes every folder but those of [kept] - the downloads that may still carry on. */
    fun discardAllBut(kept: Set<Long>) {
        root.listFiles().orEmpty().filter { it.name.toLongOrNull() !in kept }.forEach(File::deleteRecursively)
        // Where every download was fetched before, in the cache, all in one folder.
        File(context.cacheDir, "downloads").deleteRecursively()
    }
}
