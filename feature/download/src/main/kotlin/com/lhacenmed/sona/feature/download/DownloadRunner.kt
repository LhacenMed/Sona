package com.lhacenmed.sona.feature.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.downloader.DirectTransfer
import com.lhacenmed.sona.core.downloader.DownloadEvent
import com.lhacenmed.sona.core.downloader.DownloadSource
import com.lhacenmed.sona.core.downloader.EmbeddedExtras
import com.lhacenmed.sona.core.downloader.MediaResolver
import com.lhacenmed.sona.core.downloader.YtDlp
import com.lhacenmed.sona.core.downloader.YtDlpException
import com.lhacenmed.sona.core.downloader.failureOf
import com.lhacenmed.sona.core.downloader.isStaleRead
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.feature.download.work.DownloadNotifications
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Where downloads are saved: a folder of the device's Download, which the library reads as any other. */
internal fun downloadFolderOf(preferences: DownloaderPreferences): File =
    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), preferences.folderName.ifBlank { "Sona" })

/**
 * Runs the [DownloadQueue]: as many at once as the settings say, the longest waiting first - each read once (see
 * [MediaResolver]), downloaded straight from its site where its choices allow (see [DirectTransfer]) or by yt-dlp handed that
 * read, and every file saved brought into the library, carrying what its site gave it.
 */
@Singleton
class DownloadRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: DownloadQueue,
    private val dao: DownloadDao,
    private val settings: DownloadSettings,
    private val resolver: MediaResolver,
    private val ytDlp: YtDlp,
    private val directTransfer: DirectTransfer,
    private val mediaScanner: MediaScanner,
) {
    /** Runs what is queued until nothing is left that can start now. */
    suspend fun runQueue() = coroutineScope {
        val active = mutableMapOf<Long, Job>()
        while (true) {
            queue.changes.tryReceive()
            val room = settings.preferences.value.downloadsAtOnce - active.size
            if (room > 0) {
                dao.nextToRun(active.keys, System.currentTimeMillis(), room).map(DownloadEntity::toDomain).forEach { download ->
                    val job = launch { run(download) }
                    active[download.id] = job
                    queue.track(download.id, job)
                }
            }
            if (active.isEmpty()) break
            // Until one ends, or more is queued.
            select {
                active.values.forEach { job -> job.onJoin {} }
                queue.changes.onReceive {}
            }
            active.values.removeAll { !it.isActive }
        }
    }

    private suspend fun run(download: Download) {
        var running = RunningDownload(download.id, download.title)
        fun show(next: RunningDownload) {
            running = next
            queue.setRunning(download.id, next)
        }
        show(running)
        dao.setStatus(download.id, DownloadStatus.RUNNING.name)
        val saved = mutableListOf<DownloadEvent.Saved>()
        val outcome = runCatching {
            download(download, settings.preferences.value, onFound = { show(running.copy(title = it)) }) { event ->
                when (event) {
                    is DownloadEvent.Item -> show(running.copy(title = event.title, item = event.index, itemCount = event.count, downloadedBytes = null, totalBytes = null))
                    is DownloadEvent.Progress -> show(running.copy(downloadedBytes = event.downloadedBytes, totalBytes = event.totalBytes, bytesPerSecond = event.bytesPerSecond))
                    is DownloadEvent.Saved -> saved += event
                }
            }
        }
        withContext(NonCancellable) {
            // Whatever was saved before it stopped is the user's: brought into the library all the same.
            if (saved.isNotEmpty()) {
                bringIntoLibrary(saved)
                dao.setSaved(download.id, saved.joinToString("\n") { it.file.path }, saved.sumOf { it.file.length() }, System.currentTimeMillis())
            }
            val error = outcome.exceptionOrNull()
            when {
                error == null -> dao.setStatus(download.id, DownloadStatus.DONE.name)
                error is CancellationException ->
                    // Stopped with the worker, it is taken up again the next time the queue runs.
                    dao.setStatus(download.id, if (queue.wasStoppedByUser(download.id)) DownloadStatus.CANCELED.name else DownloadStatus.QUEUED.name)
                else -> dao.setStatus(download.id, DownloadStatus.FAILED.name, error.message, failureOf(error).name)
            }
            queue.setRunning(download.id, null)
            if (error !is CancellationException) DownloadNotifications.postResult(context, running.title ?: download.url, error == null)
        }
    }

    private suspend fun download(download: Download, preferences: DownloaderPreferences, onFound: (String) -> Unit, onEvent: (DownloadEvent) -> Unit) {
        val outputDir = downloadFolderOf(preferences)
        val extraction = resolver.resolve(download.url, preferences)
        val info = extraction.info
        dao.setFound(download.id, info.title, info.thumbnailUrl, info.uploader, info.site, info.durationSeconds)
        onFound(info.title)

        directTransfer.formatFor(info, download.choices, download.options)?.let { format ->
            directTransfer.download(info, format, File(context.cacheDir, "downloads/${download.id}.part"), outputDir, onEvent)
            return
        }

        // yt-dlp's own read, still good, is handed over, so the link is not read twice. NewPipe's lacks what yt-dlp writes in
        // beyond its formats - the Most replayed graph - so yt-dlp reads such a link itself.
        val infoFile = if (!extraction.isFromNewPipe && !info.isPlaylist && extraction.isFresh()) {
            File(context.cacheDir, "download-info/${download.id}.json").apply {
                parentFile?.mkdirs()
                writeText(extraction.json)
            }
        } else {
            null
        }
        try {
            ytDlp.download(DownloadSource(download.url, infoFile), download.choices, download.options, preferences, outputDir, onEvent)
        } catch (e: YtDlpException) {
            if (infoFile == null || !isStaleRead(e)) throw e
            // The read was the failure, not the link: its streams were no longer served. Read again, it downloads.
            resolver.forget(download.url)
            ytDlp.download(DownloadSource(download.url, null), download.choices, download.options, preferences, outputDir, onEvent)
        } finally {
            infoFile?.delete()
        }
    }

    /**
     * Writes into each of the [saved] files what its site gave it - see [EmbeddedExtras] - then has Android index them, and the
     * library read them in, with all they carry.
     */
    private suspend fun bringIntoLibrary(saved: List<DownloadEvent.Saved>) {
        saved.forEach { runCatching { EmbeddedExtras.write(it.file, it.heatmapJson, it.chaptersJson) } }
        val files = saved.map { it.file }
        suspendCancellableCoroutine { continuation ->
            var left = files.size
            MediaScannerConnection.scanFile(context, files.map(File::getPath).toTypedArray(), null) { _, _ ->
                synchronized(this) { if (--left == 0) continuation.resume(Unit) }
            }
        }
        runCatching { mediaScanner.refresh() }
    }
}
