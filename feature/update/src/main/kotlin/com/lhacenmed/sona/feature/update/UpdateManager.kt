package com.lhacenmed.sona.feature.update

import android.content.Context

/**
 * Launch-time coordinator that re-hydrates [UpdateRegistry] from what survived the last session, so
 * the update flow is consistent across cold starts and offline relaunches:
 *
 *  • A staged APK that is newer than the running build resumes straight to [UpdateState.Downloaded]
 *    (the install prompt) — and is reconstructed into an [AppUpdate] even if nothing else persisted,
 *    so the dialog always has something to show. An already-installed or stale APK is deleted.
 *  • A persisted "available" update re-surfaces the prompt even with no connectivity. It and the
 *    stored manifest are dropped once the running build has caught up (i.e. the update was applied).
 *
 * Called once per process from the application; the online re-check that discovers *new* updates
 * lives in MainActivity, gated on connectivity.
 */
object UpdateManager {

    fun restore(context: Context) {
        val appCtx = context.applicationContext

        // 1. Staged APK — kept only when it is a newer, not-yet-installed build (else deleted here).
        val staged = ApkDownloader.stagedUpdate(appCtx)

        // 2. Persisted manifest — valid only while it still points past the running build.
        val saved = UpdateStore.load(appCtx)?.takeIf { it.versionCode > appCtx.installedVersionCode() }
        if (saved == null) UpdateStore.clear(appCtx)

        // 3. Re-surface the prompt. Prefer the richer saved manifest (has notes); fall back to the
        //    staged APK's own version info so a download finished last session can still install.
        val available = saved ?: staged?.let {
            AppUpdate(it.versionCode, it.versionName, apkUrl = "", notes = "")
        } ?: return

        UpdateRegistry.setAvailable(available)
        if (staged != null) UpdateRegistry.update(UpdateState.Downloaded(staged.file))
    }
}
