package com.lhacenmed.sona.feature.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Installs a staged APK over the running app - the one way every update is installed, asked for by the user or
 * by [AutoUpdater] - through an install session of Sona's own rather than by handing the file to another app.
 *
 * That is what lets Android install it without asking: from Android 12 on, an app updating itself is not made
 * to confirm it once it holds the grant to install apps. Where Android asks all the same - before Android 12,
 * or without that grant - [UpdateInstallReceiver] hears so, and what happens next is its to say.
 */
object UpdateInstaller {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Whether this Android version can install an update without the user confirming it. */
    val canInstallUnattended: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** [install] from a press, which nothing waits on: the user is asked where Android wants them to be. */
    fun installNow(context: Context, staged: StagedApk) {
        scope.launch { install(context.applicationContext, staged, isUnattended = false) }
    }

    /**
     * Hands [staged] to Android to install, and returns once it has it - whether it could. The install itself
     * ends the app, so nothing here hears it succeed. [isUnattended] says no one is there to confirm it: asked
     * to, the install is let go rather than raised over whatever the user is doing.
     */
    suspend fun install(context: Context, staged: StagedApk, isUnattended: Boolean): Boolean = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val size = staged.file.length()
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(size)
            if (canInstallUnattended) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        runCatching {
            val sessionId = installer.createSession(params)
            runCatching {
                installer.openSession(sessionId).use { session ->
                    session.openWrite(staged.file.name, 0, size).use { out ->
                        staged.file.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    session.commit(statusSender(context, sessionId, staged, isUnattended))
                }
            }.onFailure { installer.abandonSession(sessionId) }.getOrThrow()
        }.isSuccess
    }

    /** What Android tells of the session's fate through - [UpdateInstallReceiver], told whose install it was. */
    private fun statusSender(context: Context, sessionId: Int, staged: StagedApk, isUnattended: Boolean) =
        PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, UpdateInstallReceiver::class.java)
                .putExtra(UpdateInstallReceiver.EXTRA_UNATTENDED, isUnattended)
                .putExtra(UpdateInstallReceiver.EXTRA_VERSION_NAME, staged.versionName),
            // Mutable: Android writes the session's status into it.
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
        ).intentSender
}
