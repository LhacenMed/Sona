package com.lhacenmed.sona.feature.tageditor.autotag

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lhacenmed.sona.core.common.notification.SonaNotificationChannel
import com.lhacenmed.sona.core.common.notification.SonaNotificationId
import com.lhacenmed.sona.core.common.notification.SonaNotifications
import com.lhacenmed.sona.core.common.notification.startForegroundCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/** How often the notification may follow the run - tracks are done far faster than the shade should redraw. */
private const val PROGRESS_INTERVAL_MS = 500L

/**
 * Keeps the app alive while [AutoTagger] works - looking tracks up, or saving them - and the shade told how far it
 * is: the run survives its screen closing and the app being swiped away. It lets itself go the moment the run waits
 * on the user, or ends - as the library rescan's own service does.
 */
@AndroidEntryPoint
class AutoTagService : Service() {

    @Inject
    lateinit var autoTagger: AutoTagger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() requires startForeground() within 5s on every delivery.
        startForegroundCompat(SonaNotificationId.AUTO_TAG, progressNotification(null))
        if (job?.isActive != true) job = followRun()
        // A run killed with the process is gone with its results: there is nothing to come back to.
        return START_NOT_STICKY
    }

    @OptIn(FlowPreview::class)
    private fun followRun() = scope.launch {
        autoTagger.progress
            .takeWhile { it != null }
            .sample(PROGRESS_INTERVAL_MS)
            .collect { SonaNotifications.post(this@AutoTagService, SonaNotificationId.AUTO_TAG, progressNotification(it)) }
        ServiceCompat.stopForeground(this@AutoTagService, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun progressNotification(progress: AutoTagProgress?): Notification {
        val text = when (progress?.phase) {
            AutoTagPhase.SAVING -> "Saving ${progress.done} of ${tracksLabel(progress.total)}"
            null -> "Looking up tracks"
            else -> "Looking up ${progress.done} of ${tracksLabel(progress.total)}"
        }
        return SonaNotifications.builder(this, SonaNotificationChannel.AUTO_TAG)
            .setContentTitle("Auto-tagging")
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(progress?.total ?: 0, progress?.done ?: 0, progress == null)
            .build()
    }

    /** Opens the app where the user left it. */
    private fun openAppIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

    companion object {
        /** Keeps the app alive for as long as the run works - no-op while it already is. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AutoTagService::class.java))
        }
    }
}
