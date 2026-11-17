package com.lhacenmed.sona.feature.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.lhacenmed.sona.core.designsystem.component.toast

/**
 * Hears what became of an install [UpdateInstaller] started. One that succeeds ends the app before it can be
 * heard of, so what arrives here is Android wanting the user to confirm it, or its failing.
 *
 * An install the user asked for is confirmed there and then, in Android's own dialog, and says so when it
 * fails. One [AutoUpdater] started is never raised over what the user is doing: it is let go, and that version
 * left for the user to install - see [AutoUpdater.leaveToUser].
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        // The user's own "no" to Android's dialog is no failure to report.
        if (status == PackageInstaller.STATUS_SUCCESS || status == PackageInstaller.STATUS_FAILURE_ABORTED) return
        val isPendingUser = status == PackageInstaller.STATUS_PENDING_USER_ACTION

        if (intent.getBooleanExtra(EXTRA_UNATTENDED, false)) {
            if (isPendingUser) {
                val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
                runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            }
            intent.getStringExtra(EXTRA_VERSION_NAME)?.let { AutoUpdater.leaveToUser(context, it) }
        } else if (isPendingUser) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else {
            context.toast(R.string.update_install_failed)
        }
    }

    internal companion object {
        const val EXTRA_UNATTENDED = "com.lhacenmed.sona.extra.UNATTENDED"
        const val EXTRA_VERSION_NAME = "com.lhacenmed.sona.extra.VERSION_NAME"
    }
}
