package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.dataStore by preferencesDataStore(name = "theme_settings")

private val DYNAMIC_THEME_ENABLED = booleanPreferencesKey("dynamic_theme_enabled")

private val CUSTOM_THEME_COLOR_ARGB = intPreferencesKey("custom_theme_color_argb")

private val ROUND_MODE = booleanPreferencesKey("round_mode")

/**
 * Persisted theme settings: the on/off toggle for artwork-derived dynamic theming, an optional
 * manual fallback color, and round mode. This class only stores settings - it does not perform any
 * color extraction and does not hold the live "current" theme color, which is runtime state
 * owned by whatever observes playback.
 */
@Singleton
class ThemeSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.dataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    val dynamicThemeEnabled: Setting<Boolean> =
        cache.setting { it[DYNAMIC_THEME_ENABLED] ?: true }

    suspend fun setDynamicThemeEnabled(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_THEME_ENABLED] = enabled }
    }

    val customThemeColorArgb: Setting<Int?> =
        cache.setting { it[CUSTOM_THEME_COLOR_ARGB] }

    suspend fun setCustomThemeColor(color: Int?) {
        dataStore.edit { preferences ->
            if (color == null) {
                preferences.remove(CUSTOM_THEME_COLOR_ARGB)
            } else {
                preferences[CUSTOM_THEME_COLOR_ARGB] = color
            }
        }
    }

    /** Whether covers are drawn with rounded corners (Auxio's `UISettings.roundMode`). */
    val roundMode: Setting<Boolean> = cache.setting { it[ROUND_MODE] ?: true }

    suspend fun setRoundMode(enabled: Boolean) {
        dataStore.edit { it[ROUND_MODE] = enabled }
    }
}
