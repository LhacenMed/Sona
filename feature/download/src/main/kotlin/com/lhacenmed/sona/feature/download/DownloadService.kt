package com.lhacenmed.sona.feature.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lhacenmed.sona.core.common.notification.SonaNotificationChannel
import com.lhacenmed.sona.core.common.notification.SonaNotificationId
import com.lhacenmed.sona.core.common.notification.SonaNotifications
import com.lhacenmed.sona.core.common.notification.startForegroundCompat
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.downloader.DownloaderMissingException
import com.lhacenmed.sona.core.downloader.DownloadEvent
import com.lhacenmed.sona.core.downloader.EmbeddedExtras
import com.lhacenmed.sona.core.downloader.YtDlp
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Runs the [DownloadQueue] to its end, one download at a time, the oldest first - in the foreground, so a download goes on
 * with the app closed. Each file saved is brought into the library, carrying what its site gave it.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var queue: DownloadQueue
    @Inject lateinit var dao: DownloadDao
    @Inject lateinit var ytDlp: YtDlp
    @Inject lateinit var mediaScanner: MediaScanner

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var queueJob: Job? = null
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(SonaNotificationId.DOWNLOAD, progressNotification(queue.running.value))
        when {
            intent?.action == ACTION_CANCEL -> downloadJob?.cancel()
            queueJob?.isActive == true -> Unit
            else -> queueJob = scope.launch { runQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun runQueue() {
        while (true) {
            queue.awaitQueued()
            val next = dao.next()?.toDomain() ?: break
            val job = scope.launch { download(next) }
            downloadJob = job
            job.join()
        }
        queue.setRunning(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun download(download: Download) {
        var running = RunningDownload(download.id, download.title)
        show(running)
        dao.setStatus(download.id, DownloadStatus.RUNNING.name)
        val saved = mutableListOf<DownloadEvent.Saved>()
        val outcome = runCatching {
            ytDlp.download(download.url, download.choices, outputDir()) { event ->
                when (event) {
                    is DownloadEvent.Item -> {
                        running = running.copy(title = event.title, item = event.index, itemCount = event.count, progress = null)
                        if (download.title == null) scope.launch { dao.setTitle(download.id, event.title) }
                    }

                    is DownloadEvent.Progress -> running = running.copy(progress = event.fraction)

                    is DownloadEvent.Saved -> {
                        saved += event
                    }
                }
                show(running)
            }
        }
        withContext(NonCancellable) {
            // Whatever was saved before it stopped is the user's: brought into the library all the same.
            if (saved.isNotEmpty()) bringIntoLibrary(saved)
            val error = outcome.exceptionOrNull()
            val status = when (error) {
                null -> DownloadStatus.DONE
                is CancellationException -> DownloadStatus.CANCELED
                else -> DownloadStatus.FAILED
            }
            dao.setStatus(download.id, status.name, error?.takeIf { status == DownloadStatus.FAILED }?.let(::reasonOf))
            if (status != DownloadStatus.CANCELED) postResult(running.title ?: download.url, status == DownloadStatus.DONE)
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
            MediaScannerConnection.scanFile(this, files.map(File::getPath).toTypedArray(), null) { _, _ ->
                synchronized(this) { if (--left == 0) continuation.resume(Unit) }
            }
        }
        runCatching { mediaScanner.refresh() }
    }

    private fun reasonOf(error: Throwable): String = when (error) {
        is DownloaderMissingException -> getString(R.string.download_failed_no_downloader)
        else -> error.message ?: getString(R.string.download_failed)
    }

    /** What the notification last said - posted again only once that changes, as a whole percent does. */
    private var shown: RunningDownload? = null

    private fun show(running: RunningDownload) {
        queue.setRunning(running)
        val asShown = running.copy(progress = running.progress?.let { (it * 100).toInt() / 100f })
        if (asShown == shown) return
        shown = asShown
        SonaNotifications.post(this, SonaNotificationId.DOWNLOAD, progressNotification(running))
    }

    private fun progressNotification(running: RunningDownload?): Notification {
        val progress = running?.progress
        return SonaNotifications.builder(this, SonaNotificationChannel.DOWNLOADS)
            .setContentTitle(running?.title ?: getString(R.string.download_starting))
            .setContentText(running?.let { statusText(it) })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            .addAction(0, getString(R.string.download_cancel), cancelIntent())
            .build()
    }

    private fun statusText(running: RunningDownload): String? {
        val percent = running.progress?.let { getString(R.string.download_percent, (it * 100).toInt()) }
        val item = if (running.item != null && running.itemCount != null) getString(R.string.download_item_of, running.item, running.itemCount) else null
        return listOfNotNull(percent, item).joinToString(" · ").ifEmpty { null }
    }

    private fun postResult(title: String, isSaved: Boolean) {
        val notification = SonaNotifications.builder(this, SonaNotificationChannel.DOWNLOADS)
            .setContentTitle(title)
            .setContentText(getString(if (isSaved) R.string.download_saved else R.string.download_failed))
            .setAutoCancel(true)
            .build()
        SonaNotifications.post(this, SonaNotificationId.DOWNLOAD_RESULT, notification)
    }

    private fun cancelIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onDestroy() {
        scope.cancel()
        queue.setRunning(null)
        super.onDestroy()
    }

    companion object {
        internal const val ACTION_CANCEL = "com.lhacenmed.sona.action.CANCEL_DOWNLOAD"

        /** Where downloads are saved: a folder of the device's Download, which the library reads as any other. */
        fun outputDir(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Sona")

        /** Runs the queue, should it not be running. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }
    }
}
