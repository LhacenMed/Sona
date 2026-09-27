package com.lhacenmed.sona.feature.update

import android.content.Context
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.github.Releases

/**
 * Decides whether a release is an update to the running build, and says so to [UpdateRegistry] - the one way
 * every check goes: at launch, from the Updates screen, from what was kept, and in the background.
 */
object UpdateChecker {

    /**
     * The newest release on [channel] - held in [UpdateRegistry] as the available update when it [isUpdate],
     * and let go when it is not, unless its download is already under way or done.
     */
    suspend fun check(context: Context, channel: UpdateChannel, forceRefresh: Boolean = false): Result<Release> =
        Releases.latest(context, channel, forceRefresh).onSuccess { latest ->
            when {
                isUpdate(context, latest) -> UpdateRegistry.setAvailable(latest)
                !UpdateRegistry.holdsDownload -> UpdateRegistry.setAvailable(null)
            }
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
