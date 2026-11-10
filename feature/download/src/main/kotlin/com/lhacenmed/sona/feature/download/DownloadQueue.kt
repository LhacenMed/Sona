package com.lhacenmed.sona.feature.download

import android.content.Context
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.queuedDownload
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadOptions
import com.lhacenmed.sona.feature.download.work.DownloadWork
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) {
    /** How far the file downloading is, from 0 to 1 - null while unknown. */
    val fraction: Float? get() = if (downloadedBytes != null && totalBytes != null && totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

/** One link to queue, with what is known of it already, and how. */
data class DownloadRequest(
    val url: String,
    val title: String?,
    val thumbnailUrl: String?,
    val uploader: String?,
    val choices: DownloadChoices,
    val options: DownloadOptions = DownloadOptions(),
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
    runtime: DownloaderRuntime,
    @ApplicationScope private val scope: CoroutineScope,
) {
    init {
        // What waited on the downloader downloads the moment it is here, wherever the user is.
        scope.launch { runtime.state.drop(1).filter { it == RuntimeState.Installed }.collect { retryFailed(DownloadFailure.NO_DOWNLOADER) } }
    }

    /** Every download, the newest first. */
    val downloads: Flow<List<Download>> = dao.observeAll().map { rows -> rows.map(DownloadEntity::toDomain) }

    private val _running = MutableStateFlow<Map<Long, RunningDownload>>(emptyMap())

    /** The downloads running, as they go, by id. */
    val running: StateFlow<Map<Long, RunningDownload>> = _running.asStateFlow()

    /** Each running download's work, by id - what cancelling one stops. */
    private val jobs = mutableMapOf<Long, Job>()

    /** The running downloads the user stopped - told apart from those stopped with the worker, which carry on next time. */
    private val stoppedByUser = mutableSetOf<Long>()

    /** Told whenever there may be more to run - what the worker waits on between downloads. */
    internal val changes = Channel<Unit>(Channel.CONFLATED)

    /** Queues [requests] - to start at [startAt], or as soon as they can - and has them run. */
    fun add(requests: List<DownloadRequest>, startAt: Long? = null) {
        if (requests.isEmpty()) return
        val now = System.currentTimeMillis()
        scope.launch {
            dao.insertAll(
                requests.mapIndexed { index, request ->
                    // Apart by a millisecond each, so they keep the order they were asked in.
                    queuedDownload(request.url, request.title, request.thumbnailUrl, request.uploader, request.choices, request.options, startAt, now + index)
                },
            )
            changed()
        }
    }

    /** Queues [url] as the user downloads - what a Quick download does. */
    fun add(url: String) = add(listOf(DownloadRequest(url, title = null, thumbnailUrl = null, uploader = null, choices = settings.choices.value)))

    /** Queues [ids] again, as they were asked for. */
    fun retry(ids: Collection<Long>) {
        scope.launch {
            dao.requeue(ids)
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

    /** Stops [ids] - there and then for those running, before they start for those waiting. */
    fun cancel(ids: Collection<Long>) {
        synchronized(jobs) {
            stoppedByUser += ids.filter(jobs::containsKey)
            ids.mapNotNull(jobs::get)
        }.forEach(Job::cancel)
        scope.launch { dao.cancelQueued(ids) }
    }

    /** Whether the user stopped [id] - asked once, as it stops. */
    internal fun wasStoppedByUser(id: Long): Boolean = synchronized(jobs) { stoppedByUser.remove(id) }

    /** Takes [ids] off the history - their files stay where they were saved. */
    fun remove(ids: Collection<Long>) {
        scope.launch { dao.delete(ids) }
    }

    /** Deletes [ids]' files, then takes them off the history. Whether every file went. */
    suspend fun deleteWithFiles(ids: Collection<Long>): Boolean {
        val downloads = dao.byIds(ids).map(DownloadEntity::toDomain)
        val allDeleted = downloads.flatMap(Download::savedPaths).map(::File).all { !it.exists() || it.delete() }
        dao.delete(ids)
        return allDeleted
    }

    /** Has the worker run what is queued - picked up at once where it already runs. */
    private fun changed() {
        changes.trySend(Unit)
        DownloadWork.start(context, settings.preferences.value)
    }

    internal fun setRunning(id: Long, running: RunningDownload?) {
        _running.update { if (running == null) it - id else it + (id to running) }
    }

    internal fun track(id: Long, job: Job) {
        synchronized(jobs) { jobs[id] = job }
        job.invokeOnCompletion { synchronized(jobs) { jobs.remove(id) } }
    }
}
