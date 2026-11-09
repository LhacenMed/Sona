package com.lhacenmed.sona.feature.download

import android.content.Context
import android.content.Intent
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.queuedDownload
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The download running: which, and how far it has got - see [DownloadQueue.running]. */
data class RunningDownload(
    val id: Long,
    val title: String?,
    /** Which of a playlist's videos it is on, and of how many - null for one video. */
    val item: Int? = null,
    val itemCount: Int? = null,
    /** How far the file downloading is, from 0 to 1 - null while unknown. */
    val progress: Float? = null,
)

/** The first link in [text] - what a share of a page gives, often with words around it. */
fun linkIn(text: String?): String? = text?.let { LinkPattern.find(it)?.value }

private val LinkPattern = Regex("""https?://\S+""")

/**
 * Every download asked for - the one record the download screen, the Quick download and the [DownloadService] that runs
 * them share. Kept in the database, so what is queued outlives the app, and run one at a time, the oldest first.
 */
@Singleton
class DownloadQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val settings: DownloadSettings,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Every download, the newest first. */
    val downloads: Flow<List<Download>> = dao.observeAll().map { rows -> rows.map(DownloadEntity::toDomain) }

    private val _running = MutableStateFlow<RunningDownload?>(null)

    /** The download running, as it goes - null with none. */
    val running: StateFlow<RunningDownload?> = _running.asStateFlow()

    /** How many writes that queue a download are on their way - which the service waits for before it reads the queue. */
    private val queueing = MutableStateFlow(0)

    /** Queues [url] to download with [choices] - the user's own unless others are given - and starts the queue. */
    fun add(url: String, choices: DownloadChoices = settings.choices.value, title: String? = null) =
        queue { dao.insert(queuedDownload(url, title, choices, System.currentTimeMillis())) }

    /** Queues [download] again, as it was asked for. */
    fun retry(download: Download) = queue { dao.setStatus(download.id, DownloadStatus.QUEUED.name) }

    /**
     * Writes a download into the queue and starts the service there and then - from the screen that asked, still in front:
     * Android lets no service start in the foreground once the app is behind others, as a Quick download's is a moment
     * later. The service waits for the write - see [awaitQueued].
     */
    private fun queue(write: suspend () -> Unit) {
        queueing.update { it + 1 }
        scope.launch {
            try {
                write()
            } finally {
                queueing.update { it - 1 }
            }
        }
        DownloadService.start(context)
    }

    /** Waits for every download being queued to be written. */
    internal suspend fun awaitQueued() {
        queueing.first { it == 0 }
    }

    /** Stops [download] - there and then while it runs, before it starts while it waits. */
    fun cancel(download: Download) {
        if (_running.value?.id == download.id) {
            context.startService(Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL))
        } else {
            scope.launch { dao.setStatus(download.id, DownloadStatus.CANCELED.name) }
        }
    }

    /** Takes every finished download off the list - its files stay where they were saved. */
    fun clearFinished() {
        scope.launch { dao.deleteFinished() }
    }

    internal fun setRunning(running: RunningDownload?) {
        _running.value = running
    }
}
