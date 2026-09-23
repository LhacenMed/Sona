package com.lhacenmed.sona.feature.update

import android.content.Context

/**
 * Persists the discovered [AppUpdate] across launches so the prompt survives a cold start. Once a
 * newer build is found online its manifest is saved here; on the next launch [UpdateManager]
 * re-hydrates [UpdateRegistry] from it — so a user who first saw the prompt online still sees it
 * after relaunching offline. Cleared once the running build has caught up to the saved version.
 */
object UpdateStore {

    private const val PREFS = "app_update"
    private const val KEY_UPDATE = "available"

    /** The update feature's own store. */
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, update: AppUpdate) {
        prefs(context).edit().putString(KEY_UPDATE, update.toJson()).apply()
    }

    fun load(context: Context): AppUpdate? =
        prefs(context).getString(KEY_UPDATE, null)?.let {
            runCatching { AppUpdate.fromJson(it) }.getOrNull()
        }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_UPDATE).apply()
    }
}
