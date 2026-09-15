package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.imageDataStore by preferencesDataStore(name = "image_settings")

private val COVER_MODE = stringPreferencesKey("cover_mode")
private val FORCE_SQUARE_COVERS = booleanPreferencesKey("force_square_covers")

/** How album covers are loaded and shaped (ported from Auxio's `ImageSettings`). */
@Singleton
class ImageSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.imageDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    val coverMode: Setting<CoverMode> = cache.setting { preferences ->
        preferences[COVER_MODE]?.let { name -> runCatching { CoverMode.valueOf(name) }.getOrNull() }
            ?: CoverMode.BALANCED
    }

    suspend fun setCoverMode(mode: CoverMode) {
        dataStore.edit { it[COVER_MODE] = mode.name }
    }

    /** Whether every cover is cropped to a 1:1 aspect ratio. */
    val forceSquareCovers: Setting<Boolean> = cache.setting { it[FORCE_SQUARE_COVERS] ?: false }

    suspend fun setForceSquareCovers(enabled: Boolean) {
        dataStore.edit { it[FORCE_SQUARE_COVERS] = enabled }
    }
}
