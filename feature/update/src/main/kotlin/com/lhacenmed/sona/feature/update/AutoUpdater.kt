package com.lhacenmed.sona.feature.update

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.edit
import com.lhacenmed.sona.core.common.lifecycle.isAppInForeground
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.notification.UpdateNotifier
import kotlinx.coroutines.flow.first

/**
 * Updates the app by itself: brings the update's APK in without a word - no sheet, no dialog, no progress in
 * the shade - and installs it while Sona is not in use, so nothing the user is doing is ever cut short.
 *
 *  1. [stage]: the APK is downloaded and verified exactly as one the user asks for is - see [ApkDownloader] -
 *     and kept where the next launch finds it, so no step is ever done twice.
 *  2. [install]: handed to Android only while Sona is idle - nothing on screen, nothing playing, nothing
 *     downloading or backing up - as installing ends the app. Busy, it waits: [UpdateDeliveryWorker] comes
 *     back to it, and [UpdateMonitor] tries the moment the app leaves the screen.
 *
 * Where Android will not install without the user - before Android 12, or when it asks all the same - the
 * update still waits downloaded, and one notification says so: the only time this is heard from.
 */
object AutoUpdater {
    private const val PREFS = "auto_updater"
    private const val KEY_LEFT_TO_USER_VERSION = "left_to_user_version"

    /**
     * [release]'s APK for the running build, staged: the one already there, or downloaded now. Null when it
     * could not be - or while a download the user started is under way, which is theirs to finish.
     */
    internal suspend fun stage(context: Context, release: Release): StagedApk? {
        if (UpdateRegistry.isActive) return null
        ApkDownloader.stagedUpdate(context)?.takeIf { it.versionName == release.versionName }?.let { return it }
        val apk = release.apkFor(context.installedBuild()) ?: return null

        var staged: StagedApk? = null
        UpdateRegistry.setAvailable(release)
        ApkDownloader.download(context, apk).collect { state ->
            // A failure is kept to itself: there is no one to tell, and the next attempt starts afresh.
            UpdateRegistry.update(if (state is UpdateState.Error) UpdateState.Idle else state)
            if (state is UpdateState.Downloaded) staged = state.staged
        }
        return staged
    }

    /**
     * Installs [staged] if now is the time to - returning false while Sona is in use, for the caller to come
     * back to. An update Android wants the user for is announced instead, once, and is done with.
     */
    suspend fun install(context: Context, staged: StagedApk): Boolean {
        if (!UpdateInstaller.canInstallUnattended || isLeftToUser(context, staged.versionName)) {
            UpdateNotifier.notifyReady(context, staged.versionName)
            return true
        }
        if (!isIdle(context)) return false
        return UpdateInstaller.install(context, staged, isUnattended = true)
    }

    /** [versionName] is the user's to install from here on - Android asked for them, or refused it. */
    internal fun leaveToUser(context: Context, versionName: String) {
        prefs(context).edit { putString(KEY_LEFT_TO_USER_VERSION, versionName) }
        UpdateNotifier.notifyReady(context, versionName)
    }

    private fun isLeftToUser(context: Context, versionName: String): Boolean =
        prefs(context).getString(KEY_LEFT_TO_USER_VERSION, null) == versionName

    /**
     * Whether ending the app now would cost the user nothing: none of its screens showing, and none of its
     * foreground services - playback, a download, a backup, a scan - at work. Read off the services Android
     * is running for the app, so a new one counts without being named here.
     */
    private suspend fun isIdle(context: Context): Boolean {
        if (isAppInForeground().first()) return false
        val activityManager = context.getSystemService(ActivityManager::class.java)
        // Deprecated for looking at other apps; an app's own services are still what it answers with.
        @Suppress("DEPRECATION")
        return activityManager.getRunningServices(Int.MAX_VALUE).none { it.foreground }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
