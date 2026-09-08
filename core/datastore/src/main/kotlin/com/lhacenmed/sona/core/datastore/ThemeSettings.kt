package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "theme_settings")

private val DYNAMIC_THEME_ENABLED = booleanPreferencesKey("dynamic_theme_enabled")

private val CUSTOM_THEME_COLOR_ARGB = intPreferencesKey("custom_theme_color_argb")

/**
 * Persisted theme settings: the on/off toggle for artwork-derived dynamic theming, and an
 * optional manual fallback color. This class only stores settings - it does not perform any
 * color extraction and does not hold the live "current" theme color, which is runtime state
 * owned by whatever observes playback.
 */
@Singleton
class ThemeSettings @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.dataStore

    val dynamicThemeEnabled: Flow<Boolean> =
        dataStore.data.map { it[DYNAMIC_THEME_ENABLED] ?: true }

    suspend fun setDynamicThemeEnabled(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_THEME_ENABLED] = enabled }
    }

    val customThemeColorArgb: Flow<Int?> =
        dataStore.data.map { it[CUSTOM_THEME_COLOR_ARGB] }

    suspend fun setCustomThemeColor(color: Int?) {
        dataStore.edit { preferences ->
            if (color == null) {
                preferences.remove(CUSTOM_THEME_COLOR_ARGB)
            } else {
                preferences[CUSTOM_THEME_COLOR_ARGB] = color
            }
        }
    }
}
