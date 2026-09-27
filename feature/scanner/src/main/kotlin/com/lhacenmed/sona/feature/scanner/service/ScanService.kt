package com.lhacenmed.sona.feature.scanner.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.core.common.notification.SonaNotificationChannel
import com.lhacenmed.sona.core.common.notification.SonaNotificationId
import com.lhacenmed.sona.core.common.notification.SonaNotifications
import com.lhacenmed.sona.core.common.notification.startForegroundCompat
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.scanner.R
import com.lhacenmed.sona.feature.scanner.ScanProgress
import com.lhacenmed.sona.feature.scanner.ScanStep
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample

/**
 * How often the notification may follow the scan. Tags are read file by file, far faster than the shade
 * should redraw - and faster than Android lets an app update a notification.
 */
private const val PROGRESS_INTERVAL_MS = 250L

/**
 * Foreground service that runs one full rescan of the device to completion, independently of the UI: it
 * survives the Storage screen closing and the app being swiped away. Progress is read from
 * [MediaScanner.progress] and mirrored to a notification; how it ended is told in a toast.
 *
 * One rescan at a time - a request while one runs is ignored; the running one already reads everything.
 * There is no Stop: a rescan takes seconds, and stopping one would only leave the library out of date.
 */
@AndroidEntryPoint
class ScanService : Service() {

    @Inject
    lateinit var mediaScanner: MediaScanner

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() requires startForeground() within 5s on every delivery.
        startForegroundCompat(SonaNotificationId.LIBRARY_SCAN, progressNotification(mediaScanner.progress.value))
        if (job?.isActive != true) startScan()
        // A rescan killed with the process is not redone: the next launch's scan finds what it missed.
        return START_NOT_STICKY
    }

    private fun startScan() {
        job = scope.launchOperation(onFinished = ::finish) {
            coroutineScope {
                val notificationUpdates = followProgress().launchIn(this)
                mediaScanner.rescan(force = true)
                notificationUpdates.cancel()
            }
        }
    }

    /** The scan's progress, mirrored to the notification as often as it may be redrawn. */
    @OptIn(FlowPreview::class)
    private fun followProgress() = mediaScanner.progress
        .filterNotNull()
        .sample(PROGRESS_INTERVAL_MS)
        .onEach { SonaNotifications.post(this, SonaNotificationId.LIBRARY_SCAN, progressNotification(it)) }

    /** Tells how the rescan ended - wherever the user is by now - and lets the service go. */
    private fun finish(succeeded: Boolean) {
        toast(if (succeeded) R.string.scan_done else R.string.scan_failed)
        stopNow()
    }

    /** Removes the ongoing notification and stops the service. Safe to call more than once. */
    private fun stopNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────────────────

    /** The scan's step - with how many files are read, while that is known - or its first, before it starts. */
    private fun progressNotification(progress: ScanProgress?): Notification {
        val fraction = progress?.fraction
        return SonaNotifications.builder(this, SonaNotificationChannel.LIBRARY_SCAN)
            .setContentTitle(getString(R.string.scan_notification_title))
            .setContentText(progress.text())
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
            .build()
    }

    private fun ScanProgress?.text(): String = when (this?.step) {
        null, ScanStep.READING_MEDIA_STORE -> getString(R.string.scan_step_reading_media_store)
        ScanStep.SEARCHING_STORAGE -> getString(R.string.scan_step_searching_storage)
        ScanStep.READING_TAGS -> getString(R.string.scan_step_reading_tags, done, total)
        ScanStep.SAVING -> getString(R.string.scan_step_saving)
    }

    /** Opens the app where the user left it. */
    private fun openAppIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

    companion object {
        /** Rescans every file on the device, in the background (no-op if a rescan is already running). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java))
        }
    }
}
