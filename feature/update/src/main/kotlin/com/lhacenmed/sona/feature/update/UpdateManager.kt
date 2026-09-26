package com.lhacenmed.sona.feature.update

import android.content.Context
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.github.Releases

/**
 * Launch-time coordinator that re-hydrates [UpdateRegistry] from what survived the last session, so the
 * update flow is consistent across cold starts and offline relaunches:
 *
 *  - A staged APK that is newer than the running build resumes straight to [UpdateState.Downloaded] (the
 *    install prompt), under its release when the kept releases still have it. An already-installed or
 *    stale APK is deleted.
 *  - Otherwise the newest kept release on [UpdateChannel] re-surfaces the prompt even with no
 *    connectivity, while it is still newer than the running build.
 *
 * Called once per process from the application; the online check that discovers *new* releases runs
 * from MainActivity, gated on connectivity.
 */
object UpdateManager {

    fun restore(context: Context, channel: UpdateChannel) {
        val appContext = context.applicationContext
        val kept = Releases.cached(appContext, channel)

        val staged = ApkDownloader.stagedUpdate(appContext)
        if (staged != null) {
            val release = kept.firstOrNull { it.versionName == staged.versionName }
                ?: Release(
                    tagName = "v${staged.versionName}",
                    versionName = staged.versionName,
                    isPreRelease = '-' in staged.versionName,
                    notes = null,
                    publishedAt = "",
                    htmlUrl = "",
                    apkUrl = null,
                )
            UpdateRegistry.setAvailable(release)
            UpdateRegistry.update(UpdateState.Downloaded(staged.file))
            return
        }

        kept.firstOrNull()?.takeIf { UpdateChecker.isNewer(appContext, it) }?.let(UpdateRegistry::setAvailable)
    }
}
