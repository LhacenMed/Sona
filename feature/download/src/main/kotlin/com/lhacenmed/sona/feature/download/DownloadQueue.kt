package com.lhacenmed.sona.feature.download

import android.content.Context
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.queuedDownload
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.downloader.DownloadWorkspaces
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.LiveDownloads
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.downloader.fractionOf
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadBatch
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadOptions
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.feature.download.work.DownloadWork
import com.lhacenmed.sona.feature.tageditor.FileExistsException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A download running: which, and how far it has got - see [DownloadQueue.running]. */
data class RunningDownload(
    val id: Long,
    val title: String?,
    /** Which of a playlist's videos it is on, and of how many - null for one video. */
    val item: Int? = null,
    val itemCount: Int? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long? = null,
    /** The last line the downloader wrote - what it is doing, in its own words. */
    val line: String? = null,
    /** Whether what it downloaded is being made ready - tagged, covered, given its lyrics - before it is saved. */
    val isFinishing: Boolean = false,
    /** Whether it has fetched since it began - its figures its own, not those it was resumed from. */
    val isFetching: Boolean = false,
) {
    /** How far the file downloading is, from 0 to 1 - null while unknown. */
    val fraction: Float? get() = fractionOf(downloadedBytes, totalBytes)

    /** Whether all it fetches is in, and it is made ready - merged, converted, tagged - before it is saved. */
    val isFinishingUp: Boolean get() = isFinishing || fraction == 1f
}

/** How far a download paused had got: bytes downloaded and of how many, each null where unknown - see [DownloadQueue.held]. */
typealias HeldProgress = Pair<Long?, Long?>

/**
 * How a running download was stopped: to carry on later from its last byte, for good with what it fetched - or, [RESTART], to
 * be queued again at once, from its last byte, as the downloader is taken away under it.
 */
internal enum class DownloadStop { PAUSE, CANCEL, RESTART }

/** One link to queue, with what is known of it already, and how. */
data class DownloadRequest(
    val url: String,
    val title: String?,
    val thumbnailUrl: String?,
    val uploader: String?,
    val choices: DownloadChoices,
    val options: DownloadOptions = DownloadOptions(),
    /** The playlist or watched channel it is queued with - null for one link alone. */
    val batch: DownloadBatch? = null,
)

/** The first link in [text] - what a share of a page gives, often with words around it. */
fun linkIn(text: String?): String? = text?.let { LinkPattern.find(it)?.value }

private val LinkPattern = Regex("""https?://\S+""")

/**
 * Every download asked for - the one record the download screen, a Quick download, the watched channels and the worker that runs
 * them share. Kept in the database, so what is queued outlives the app, and what is over stays as the history.
 */
@Singleton
class DownloadQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val settings: DownloadSettings,
    private val workspaces: DownloadWorkspaces,
    private val logs: DownloadLogs,
    private val live: LiveDownloads,
    runtime: DownloaderRuntime,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** What the user has just paused, resumed or cancelled, by id - as it is shown until the database says so too. */
    private val tapped = MutableStateFlow<Map<Long, DownloadStatus>>(emptyMap())

    /**
     * Every download, the newest first - each the user has just paused, resumed or cancelled shown so at once, not as it stood
     * for the moment the database takes to say so; let go once it does, or once the download is over another way.
     */
    val downloads: Flow<List<Download>> = dao.observeAll()
        .map { rows -> rows.map(DownloadEntity::toDomain) }
        .combine(tapped) { downloads, tapped ->
            if (tapped.isEmpty()) return@combine downloads
            // Each tap the database says too - or the download over another way - let go of, once this list shows it: let go of
            // before, the list would be shown a moment as the database stood before it did.
            val byId = downloads.associateBy(Download::id)
            val settled = tapped.filter { (id, status) -> byId[id]?.status?.let { it == status || it.isFinished } ?: true }.keys
            if (settled.isNotEmpty()) this@DownloadQueue.tapped.update { it - settled }
            downloads.map { download -> tapped[download.id]?.takeUnless { download.id in settled }?.let { download.copy(status = it) } ?: download }
        }
        // One list for every reader: each reading its own would let go of a tap the others still show it by, a frame before
        // their database says so too.
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val _running = MutableStateFlow<Map<Long, RunningDownload>>(emptyMap())

    /** The downloads running, as they go, by id. */
    val running: StateFlow<Map<Long, RunningDownload>> = _running.asStateFlow()

    private val _held = MutableStateFlow<Map<Long, HeldProgress>>(emptyMap())

    /**
     * How far each paused download had got, by id - held as it is paused, so its row shows it the moment it stops; read back
     * from its folder for those paused before the app last stopped - see [DownloadWorkspaces.saveProgress].
     */
    val held: StateFlow<Map<Long, HeldProgress>> = _held.asStateFlow()

    /** Each running download's work, by id - what cancelling one stops. */
    private val jobs = mutableMapOf<Long, Job>()

    /** The running downloads the user stopped, and how - told apart from those stopped with the worker, which carry on next time. */
    private val stoppedByUser = mutableMapOf<Long, DownloadStop>()

    /** Told whenever there may be more to run - what the worker waits on between downloads. */
    internal val changes = Channel<Unit>(Channel.CONFLATED)

    // After every property: what it launches runs on other threads at once, and must find them made.
    init {
        // What waited on the downloader downloads the moment it is here, wherever the user is.
        scope.launch { runtime.state.drop(1).filter { it == RuntimeState.Installed }.collect { retryFailed(DownloadFailure.NO_DOWNLOADER) } }
        // yt-dlp goes on from the files it has open once they are deleted, so the downloader taken away, every running download
        // starts again: what still has all it needs carries on from its last byte, and what needs the downloader waits for it -
        // as any download queued without it does - and carries on the moment it is back.
        scope.launch {
            var wasInstalled = runtime.state.value == RuntimeState.Installed
            runtime.state.collect { state ->
                if (wasInstalled && state == RuntimeState.Missing) stop(synchronized(jobs) { jobs.keys.toList() }, DownloadStop.RESTART)
                wasInstalled = state == RuntimeState.Installed
            }
        }
        // Each paused before the app last stopped as it was: as far as it had got, and playable as far as that.
        scope.launch {
            val paused = dao.pausedIds()
            val saved = paused.mapNotNull { id -> workspaces.progressOf(id)?.let { id to it } }.toMap()
            _held.update { saved + it }
            paused.forEach { id -> live.restore(id, workspaces.partsOf(id)) }
        }
        // What was queued, or cut off as the app last stopped, runs as the app starts - never left waiting on a run that ended.
        scope.launch { if (dao.nextToRun(emptyList(), System.currentTimeMillis(), limit = 1).isNotEmpty()) changed() }
    }

    /** Queues [requests] - to start at [startAt], or as soon as they can - and has them run. */
    fun add(requests: List<DownloadRequest>, startAt: Long? = null) {
        if (requests.isEmpty()) return
        val now = System.currentTimeMillis()
        scope.launch {
            dao.insertAll(
                requests.mapIndexed { index, request ->
                    // Apart by a millisecond each, so they keep the order they were asked in.
                    queuedDownload(request.url, request.title, request.thumbnailUrl, request.uploader, request.choices, request.options, startAt, now + index, request.batch)
                },
            )
            changed()
        }
    }

    /** Queues [url] as the user downloads - what a Quick download does. */
    fun add(url: String) = add(listOf(DownloadRequest(url, title = null, thumbnailUrl = null, uploader = null, choices = settings.choices.value)))

    /** Queues [ids] again, as they were asked for. */
    fun retry(ids: Collection<Long>) {
        tap(ids, DownloadStatus.QUEUED)
        scope.launch {
            dao.requeue(ids)
            changed()
        }
    }

    /** Queues [ids] again, saved though they are in the library already. */
    fun retryAnyway(ids: Collection<Long>) {
        scope.launch {
            dao.requeueAgain(ids)
            changed()
        }
    }

    /** Queues [id] again at the best format its choices pick, the one it was asked for being no longer offered. */
    fun retryAtBestFormat(id: Long) {
        scope.launch {
            dao.clearFormat(id)
            dao.requeue(listOf(id))
            changed()
        }
    }

    /** Queues again every download that failed for [failure] - now put right: the downloader is here, the user signed in. */
    fun retryFailed(failure: DownloadFailure) {
        scope.launch {
            dao.requeueFailed(failure.name)
            changed()
        }
    }

    /** Takes everything over off the list - their files stay where they were saved. */
    suspend fun clearHistory() = dao.deleteFinished()

    /**
     * Stops [ids] for good, and deletes what they fetched - there and then for those running, before they start for those
     * waiting or paused.
     */
    fun cancel(ids: Collection<Long>) {
        tap(ids, DownloadStatus.CANCELED)
        ids.forEach(::release)
        // A paused one played is over: its player waits for it no more.
        ids.forEach { live.finish(it, saved = null) }
        val running = stop(ids, DownloadStop.CANCEL)
        scope.launch {
            dao.cancelQueued(ids)
            workspaces.discard(ids - running)
        }
    }

    /**
     * Pauses [ids], keeping what they fetched to carry on from its last byte - see [resume]: shown paused, as far as they had got,
     * the moment they are told to, those running stopping after.
     */
    fun pause(ids: Collection<Long>) {
        tap(ids, DownloadStatus.PAUSED)
        val running = _running.value
        ids.forEach { id -> running[id]?.let { hold(id, it.downloadedBytes to it.totalBytes) } }
        scope.launch {
            dao.pause(ids)
            stop(ids, DownloadStop.PAUSE)
        }
    }

    /** Shows [ids] as [status] at once - see [downloads]. */
    private fun tap(ids: Collection<Long>, status: DownloadStatus) = tapped.update { it + ids.associateWith { status } }

    /** Carries on [ids], paused, from their last byte. */
    fun resume(ids: Collection<Long>) = retry(ids)

    /** Stops those of [ids] running, as [how]. Which they were. */
    private fun stop(ids: Collection<Long>, how: DownloadStop): Set<Long> {
        val running = synchronized(jobs) {
            ids.filter(jobs::containsKey).onEach { stoppedByUser[it] = how }.associateWith(jobs::getValue)
        }
        running.values.forEach(Job::cancel)
        return running.keys
    }

    /** How the user stopped [id] - asked once, as it stops; null where it was stopped with the worker. */
    internal fun stopOf(id: Long): DownloadStop? = synchronized(jobs) { stoppedByUser.remove(id) }

    /** Takes [ids] off the history - their files stay where they were saved. */
    fun remove(ids: Collection<Long>) {
        scope.launch {
            dao.delete(ids)
            workspaces.discard(ids)
            logs.discard(ids)
        }
    }

    /** Deletes [ids]' files, then takes them off the history. Whether every file went. */
    suspend fun deleteWithFiles(ids: Collection<Long>): Boolean {
        val downloads = dao.byIds(ids).map(DownloadEntity::toDomain)
        val allDeleted = downloads.flatMap(Download::savedPaths).map(::File).all { !it.exists() || it.delete() }
        dao.delete(ids)
        workspaces.discard(ids)
        logs.discard(ids)
        return allDeleted
    }

    /** Renames [from], a file saved that the library does not hold - subtitles, any other file - to [to], its download following it. */
    suspend fun renameSaved(from: File, to: File) = withContext(Dispatchers.IO) {
        if (to.exists()) throw FileExistsException(to)
        check(from.renameTo(to)) { "Could not rename ${from.name}" }
        dao.moveSavedPath(from.path, to.path)
    }

    /** Has the worker run what is queued - picked up at once where it already runs. */
    private fun changed() {
        changes.trySend(Unit)
        DownloadWork.start(context, settings.preferences.value)
    }

    /**
     * Holds [progress] as how far [id] has got - as it is paused, kept until it is over: never less far than it held already,
     * which a figure read as it starts again may say for a moment.
     */
    internal fun hold(id: Long, progress: HeldProgress) {
        val further = fractionOf(progress.first, progress.second) ?: return
        _held.update { held -> if ((held[id]?.let { fractionOf(it.first, it.second) } ?: -1f) > further) held else held + (id to progress) }
    }

    /** How far [id] had got as it was paused - what it starts from as it runs again. */
    internal fun heldOf(id: Long): HeldProgress? = _held.value[id]

    /** Lets go of what [id] held - over: saved, failed or cancelled. */
    internal fun release(id: Long) = _held.update { it - id }

    internal fun setRunning(id: Long, running: RunningDownload?) {
        _running.update { if (running == null) it - id else it + (id to running) }
    }

    internal fun track(id: Long, job: Job) {
        synchronized(jobs) { jobs[id] = job }
        job.invokeOnCompletion { synchronized(jobs) { jobs.remove(id) } }
    }
}
