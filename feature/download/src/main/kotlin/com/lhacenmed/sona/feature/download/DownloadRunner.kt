package com.lhacenmed.sona.feature.download

import android.content.Context
import android.media.MediaScannerConnection
import com.lhacenmed.sona.core.common.network.NetworkMonitor
import com.lhacenmed.sona.core.common.storage.storageFolderAt
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.downloader.DirectTransfer
import com.lhacenmed.sona.core.downloader.DownloadEvent
import com.lhacenmed.sona.core.downloader.DownloadSource
import com.lhacenmed.sona.core.downloader.DownloadWorkspaces
import com.lhacenmed.sona.core.downloader.DownloadedBeforeException
import com.lhacenmed.sona.core.downloader.EmbeddedExtras
import com.lhacenmed.sona.core.downloader.Extraction
import com.lhacenmed.sona.core.downloader.LiveDownloads
import com.lhacenmed.sona.core.downloader.MediaInfo
import com.lhacenmed.sona.core.downloader.MediaResolver
import com.lhacenmed.sona.core.downloader.PlayerHotspots
import com.lhacenmed.sona.core.downloader.SavedCovers
import com.lhacenmed.sona.core.downloader.YtDlp
import com.lhacenmed.sona.core.downloader.YtDlpException
import com.lhacenmed.sona.core.downloader.failureOf
import com.lhacenmed.sona.core.downloader.isStaleRead
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.feature.download.work.DownloadNotifications
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.tageditor.SongTagging
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** What stops a download already in the library - the copy found at [path]. See [LibraryCopies]. */
private class AlreadySavedException(path: String) : Exception("Already in the library: $path")

/** Where [kind] is saved: the folder picked for it, which the library reads as any other. */
internal fun downloadFolderOf(preferences: DownloaderPreferences, kind: DownloadKind): File = storageFolderAt(preferences.folderFor(kind))

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
    private val playerHotspots: PlayerHotspots,
    private val mediaScanner: MediaScanner,
    private val workspaces: DownloadWorkspaces,
    private val networkMonitor: NetworkMonitor,
    private val logs: DownloadLogs,
    private val live: LiveDownloads,
    private val libraryCopies: LibraryCopies,
) {
    /**
     * Runs what is queued until nothing is left that can start now. Whether any of it waits for the connection to come back -
     * cut off by the device going offline, to carry on from its last byte once it is online.
     */
    suspend fun runQueue(): Boolean = coroutineScope {
        // What is fetched is kept only for the downloads that may still carry on.
        workspaces.discardAllBut(dao.resumableIds().toSet())
        logs.discardAllBut(dao.ids().toSet())
        val active = mutableMapOf<Long, Job>()
        val waitingForNetwork = ConcurrentHashMap.newKeySet<Long>()
        while (true) {
            queue.changes.tryReceive()
            val room = settings.preferences.value.downloadsAtOnce - active.size
            if (room > 0) {
                dao.nextToRun(active.keys + waitingForNetwork, System.currentTimeMillis(), room).map(DownloadEntity::toDomain).forEach { download ->
                    val job = launch { if (run(download)) waitingForNetwork += download.id }
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
        waitingForNetwork.isNotEmpty()
    }

    /** Runs [download] to its end, or until it is stopped. Whether it waits for the connection to come back. */
    private suspend fun run(download: Download): Boolean {
        var running = RunningDownload(download.id, download.title)
        fun show(next: RunningDownload) {
            running = next
            queue.setRunning(download.id, next)
        }
        show(running)
        dao.setStatus(download.id, DownloadStatus.RUNNING.name)
        val saved = mutableListOf<DownloadEvent.Saved>()
        var pageHeatmap: String? = null
        var picture = download.thumbnailUrl
        val workspace = workspaces.of(download.id)
        live.forget(download.id)
        queue.release(download.id)
        val log = logs.open(download.id)
        log.append("[sona] Downloading ${download.url} as ${download.choices.kind.name.lowercase()}")
        val outcome = runCatching {
            pageHeatmap = download(download, settings.preferences.value, workspace, onFound = { found ->
                show(running.copy(title = found.title))
                picture = found.thumbnailUrl ?: picture
            }) { event ->
                when (event) {
                    is DownloadEvent.Item -> show(running.copy(title = event.title, item = event.index, itemCount = event.count, downloadedBytes = null, totalBytes = null))
                    is DownloadEvent.Progress -> {
                        show(running.copy(downloadedBytes = event.downloadedBytes, totalBytes = event.totalBytes, bytesPerSecond = event.bytesPerSecond))
                        // The file playing, not the whole: what of it a seek bar can reach.
                        live.setFraction(download.id, event.fileFraction ?: event.fraction)
                    }
                    is DownloadEvent.Part -> live.addPart(download.id, event)
                    is DownloadEvent.Log -> {
                        log.append(event.line)
                        show(running.copy(line = event.line))
                    }
                    is DownloadEvent.Saved -> {
                        saved += event
                        log.append("[sona] Saved ${event.file.path}")
                    }
                }
            }
        }
        return withContext(NonCancellable) {
            if (outcome.isSuccess) show(running.copy(isFinishing = true))
            // A song saved as the song it is: found, tagged, covered and given its lyrics, before the library reads it.
            if (outcome.isSuccess && download.choices.kind == DownloadKind.AUDIO && download.choices.savesAsSong) {
                log.append("[sona] Tagging as the song it is")
                // The song as it stands now - picked, or found as it began.
                val song = dao.byId(download.id)?.song
                saved.forEach { runCatching { SongTagging.tag(context, it.file, song) } }
            }
            // Its cover - the one now in each file, the song's where it was saved as one - as a picture of its own beside it.
            val covers = if (download.choices.savesCover) saved.mapNotNull { SavedCovers.saveBeside(it.file, picture) } else emptyList()
            // Whatever was saved before it stopped is the user's: brought into the library all the same.
            if (saved.isNotEmpty()) {
                bringIntoLibrary(saved.map { if (it.heatmapJson == null && pageHeatmap != null) it.copy(heatmapJson = pageHeatmap) else it }, covers)
                dao.setSaved(download.id, saved.joinToString("\n") { it.file.path }, saved.sumOf { it.file.length() }, System.currentTimeMillis())
            }
            // Played as it downloaded, it is played on from the file saved - before what it was fetched into goes.
            live.finish(download.id, saved.firstOrNull()?.file?.takeIf { outcome.isSuccess })
            val error = outcome.exceptionOrNull()
            // Cut off by the device going offline, not failed: queued again, to carry on once it is back.
            val failure = error?.let { if (it is AlreadySavedException || it is DownloadedBeforeException) DownloadFailure.ALREADY_SAVED else failureOf(it) }
            val waitsForNetwork = error != null && error !is CancellationException && failure == DownloadFailure.NO_NETWORK &&
                !networkMonitor.isOnline.value
            when {
                error == null -> {
                    dao.setStatus(download.id, DownloadStatus.DONE.name)
                    workspaces.discard(listOf(download.id))
                }
                error is CancellationException -> when (queue.stopOf(download.id)) {
                    DownloadStop.CANCEL -> {
                        dao.setStatus(download.id, DownloadStatus.CANCELED.name)
                        workspaces.discard(listOf(download.id))
                    }
                    DownloadStop.PAUSE -> {
                        workspaces.saveProgress(download.id, running.downloadedBytes, running.totalBytes)
                        queue.hold(download.id, running.downloadedBytes to running.totalBytes)
                        dao.setStatus(download.id, DownloadStatus.PAUSED.name)
                    }
                    // Stopped with the worker, it is taken up again the next time the queue runs.
                    null -> dao.setStatus(download.id, DownloadStatus.QUEUED.name)
                }
                waitsForNetwork -> dao.setStatus(download.id, DownloadStatus.QUEUED.name, failure = DownloadFailure.NO_NETWORK.name)
                else -> dao.setStatus(download.id, DownloadStatus.FAILED.name, error.message, checkNotNull(failure).name)
            }
            log.append(
                when {
                    error == null -> "[sona] Done"
                    error is CancellationException -> "[sona] Stopped"
                    waitsForNetwork -> "[sona] Offline: carrying on once the connection is back"
                    else -> "ERROR: ${error.message}"
                },
            )
            log.close()
            queue.setRunning(download.id, null)
            if (error !is CancellationException && !waitsForNetwork) DownloadNotifications.postResult(context, running.title ?: download.url, failure)
            waitsForNetwork
        }
    }

    /**
     * Downloads [download], saying what it does to [onEvent]. The Most replayed graph its page hands its player, where it has
     * one - see [PlayerHotspots] - read beside the download: for what is saved with no graph of its own.
     */
    private suspend fun download(
        download: Download,
        preferences: DownloaderPreferences,
        workspace: File,
        onFound: (MediaInfo) -> Unit,
        onEvent: (DownloadEvent) -> Unit,
    ): String? = coroutineScope {
        val outputDir = downloadFolderOf(preferences, download.choices.kind)
        val extraction = resolver.resolveForDownload(download.url, preferences)
        val info = extraction.info
        dao.setFound(download.id, info.title, info.thumbnailUrl, info.uploader, info.site, info.durationSeconds)
        onFound(info)
        // A sound is found as the song it is first - picked, or looked for now: kept with it where it is saved as that song, so
        // it is shown as one as it downloads and tagged as the very one shown; and looked for in the library as that song.
        val isSound = download.choices.kind == DownloadKind.AUDIO && !info.isPlaylist
        val plan = if (isSound) download.options.song ?: SongTagging.planFor(info.track ?: info.title, info.artist ?: info.uploader) else null
        if (plan != null && download.options.song == null && download.choices.savesAsSong) dao.setSong(download.id, plan)
        // Already in the library, it is not downloaded again unless asked for once told so.
        if (!download.options.downloadsAgain && !info.isPlaylist && download.choices.kind.isMedia) {
            val song = plan?.let(SongTagging::summaryOf)
            val query = CopyQuery(
                download.url, info.id ?: videoIdOf(download.url), download.choices.kind, info.title,
                song?.title ?: info.track ?: info.title, song?.artist ?: info.artist ?: info.uploader, info.durationSeconds,
            )
            libraryCopies.of(listOf(query))[download.url]?.let { throw AlreadySavedException(it.first().path) }
        }
        // One video's page, read as no one - not where the user goes by way of a proxy.
        val pageHeatmap = info.durationSeconds?.takeIf { !info.isPlaylist && preferences.proxy.isBlank() && download.choices.kind.isMedia }?.let { duration ->
            async { runCatching { playerHotspots.heatmapJson(download.url, duration) }.getOrNull() }
        }
        transfer(download, extraction, preferences, workspace, outputDir, onEvent)
        pageHeatmap?.await()
    }

    private suspend fun transfer(
        download: Download,
        extraction: Extraction,
        preferences: DownloaderPreferences,
        workspace: File,
        outputDir: File,
        onEvent: (DownloadEvent) -> Unit,
    ) {
        val info = extraction.info
        // The saved commands added to its kind and for its link, as they stand when it starts, beside the one picked.
        val options = download.options.copy(commandArguments = dao.commandArgumentsFor(download.choices.kind, download.url, download.options.commandArguments))
        directTransfer.formatFor(info, download.choices, options)?.let { format ->
            directTransfer.download(info, format, workspace, outputDir, onEvent)
            return
        }

        // The read, still good, is handed over, so yt-dlp does not read the link again - and the captions to embed are chosen
        // from it. A playlist's videos are each read as they are downloaded.
        val infoFile = if (!info.isPlaylist && extraction.isFresh()) {
            File(context.cacheDir, "download-info/${download.id}.json").apply {
                parentFile?.mkdirs()
                writeText(extraction.json)
            }
        } else {
            null
        }
        try {
            ytDlp.download(DownloadSource(download.url, infoFile), download.choices, options, preferences, workspace, outputDir, onEvent)
        } catch (e: YtDlpException) {
            if (infoFile == null || !isStaleRead(e)) throw e
            // The read was the failure, not the link: its streams were no longer served. Read again, it downloads.
            resolver.forget(download.url)
            ytDlp.download(DownloadSource(download.url, null), download.choices, options, preferences, workspace, outputDir, onEvent)
        } finally {
            infoFile?.delete()
        }
    }

    /**
     * Writes into each of the [saved] files what its site gave it - see [EmbeddedExtras] - then has Android index them and the
     * [covers] saved beside them, and the library read them in, with all they carry.
     */
    private suspend fun bringIntoLibrary(saved: List<DownloadEvent.Saved>, covers: List<File>) {
        saved.forEach { runCatching { EmbeddedExtras.write(it.file, it.heatmapJson, it.chaptersJson) } }
        val files = saved.map { it.file } + covers
        suspendCancellableCoroutine { continuation ->
            var left = files.size
            MediaScannerConnection.scanFile(context, files.map(File::getPath).toTypedArray(), null) { _, _ ->
                synchronized(this) { if (--left == 0) continuation.resume(Unit) }
            }
        }
        runCatching { mediaScanner.refresh() }
    }
}
