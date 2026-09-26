package com.lhacenmed.sona.feature.update

import android.content.Context
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.github.Releases

/**
 * Decides whether a newer build exists on a channel, and says so to [UpdateRegistry] - the one way every
 * check goes: at launch, from the Updates screen, and in the background.
 */
object UpdateChecker {

    /**
     * The newest release on [channel] - held in [UpdateRegistry] as the available update when it is newer
     * than the running build, and let go when it is not, unless its download is already under way or done.
     */
    suspend fun check(context: Context, channel: UpdateChannel, forceRefresh: Boolean = false): Result<Release> =
        Releases.latest(context, channel, forceRefresh).onSuccess { latest ->
            when {
                isNewer(context, latest) -> UpdateRegistry.setAvailable(latest)
                !UpdateRegistry.holdsDownload -> UpdateRegistry.setAvailable(null)
            }
        }

    /** Whether [release] is newer than the running build. */
    fun isNewer(context: Context, release: Release): Boolean {
        val installed = context.installedVersion() ?: return false
        val candidate = release.version ?: return false
        return candidate > installed
    }
}
