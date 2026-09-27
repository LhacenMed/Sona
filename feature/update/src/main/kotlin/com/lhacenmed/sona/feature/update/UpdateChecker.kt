package com.lhacenmed.sona.feature.update

import android.content.Context
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.github.Releases

/**
 * Decides whether a release is an update to the running build, and says so to [UpdateRegistry] - the one way
 * every check goes: [UpdateMonitor]'s while the app is open, the Updates screen's, and the background one.
 */
object UpdateChecker {

    /**
     * The newest release on [channel] as GitHub has it now - failing when GitHub could not be reached, so a
     * check is never taken as answered by what was kept from before. Held in [UpdateRegistry] as the latest
     * release, and as the available update when it [isUpdate], let go when it is not. While an update's
     * download is under way or done, that update stays the available one: its APK is what the prompt installs.
     */
    suspend fun check(context: Context, channel: UpdateChannel, forceRefresh: Boolean = false): Result<Release> =
        Releases.latest(context, channel, forceRefresh).onSuccess { latest ->
            UpdateRegistry.setLatest(latest)
            if (!UpdateRegistry.holdsDownload) UpdateRegistry.setAvailable(latest.takeIf { isUpdate(context, it) })
        }

    /**
     * Whether [release] updates the running build: a build that can be updated in place - never a debug one -
     * to a newer version, with an APK it can install - see [Release.apkFor].
     */
    fun isUpdate(context: Context, release: Release): Boolean {
        val build = context.installedBuild()
        if (!build.isUpdatable) return false
        val installed = build.version ?: return false
        val candidate = release.version ?: return false
        return candidate > installed && release.apkFor(build) != null
    }
}
