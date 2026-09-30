package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.model.FastScrollTouchArea
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.dataStore by preferencesDataStore(name = "library_settings")

private val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")

// Stored as the set of *hidden* tabs (not visible ones), so a tab added later is visible to everyone
// who has chosen theirs - and a fresh datastore means [DEFAULT_HIDDEN_TABS], with no seed step.
private val HIDDEN_TABS = stringSetPreferencesKey("hidden_tabs")

/** Folders are hidden until asked for: the Videos tab and the music tabs already reach every file. */
private val DEFAULT_HIDDEN_TABS = setOf(LibraryTab.FOLDERS.name)

private val INTELLIGENT_SORTING_ENABLED = booleanPreferencesKey("intelligent_sorting_enabled")

private val FAST_SCROLL_TOUCH_AREA = stringPreferencesKey("fast_scroll_touch_area")

private val PULL_TO_REFRESH_ENABLED = booleanPreferencesKey("pull_to_refresh_enabled")

@Singleton
class LibrarySettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.dataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    val excludedFolders: Setting<Set<String>> = cache.setting { it[EXCLUDED_FOLDERS] ?: emptySet() }

    suspend fun addExcludedFolder(path: String) {
        dataStore.edit { it[EXCLUDED_FOLDERS] = (it[EXCLUDED_FOLDERS] ?: emptySet()) + path }
    }

    suspend fun removeExcludedFolder(path: String) {
        dataStore.edit { it[EXCLUDED_FOLDERS] = (it[EXCLUDED_FOLDERS] ?: emptySet()) - path }
    }

    val visibleTabs: Setting<Set<LibraryTab>> = cache.setting { preferences ->
        val hidden = (preferences[HIDDEN_TABS] ?: DEFAULT_HIDDEN_TABS).mapNotNullTo(mutableSetOf()) { name ->
            runCatching { LibraryTab.valueOf(name) }.getOrNull()
        }
        LibraryTab.entries.toSet() - hidden
    }

    suspend fun setTabVisible(tab: LibraryTab, visible: Boolean) {
        dataStore.edit { preferences ->
            val hidden = (preferences[HIDDEN_TABS] ?: DEFAULT_HIDDEN_TABS).toMutableSet()
            if (visible) hidden.remove(tab.name) else hidden.add(tab.name)
            preferences[HIDDEN_TABS] = hidden
        }
    }

    val intelligentSortingEnabled: Setting<Boolean> =
        cache.setting { it[INTELLIGENT_SORTING_ENABLED] ?: true }

    suspend fun setIntelligentSortingEnabled(enabled: Boolean) {
        dataStore.edit { it[INTELLIGENT_SORTING_ENABLED] = enabled }
    }

    val fastScrollTouchArea: Setting<FastScrollTouchArea> = cache.setting { preferences ->
        preferences[FAST_SCROLL_TOUCH_AREA]
            ?.let { name -> runCatching { FastScrollTouchArea.valueOf(name) }.getOrNull() }
            ?: FastScrollTouchArea.Default
    }

    suspend fun setFastScrollTouchArea(touchArea: FastScrollTouchArea) {
        dataStore.edit { it[FAST_SCROLL_TOUCH_AREA] = touchArea.name }
    }

    /** Whether pulling a library tab's list down past its top rescans the library. */
    val pullToRefreshEnabled: Setting<Boolean> = cache.setting { it[PULL_TO_REFRESH_ENABLED] ?: true }

    suspend fun setPullToRefreshEnabled(enabled: Boolean) {
        dataStore.edit { it[PULL_TO_REFRESH_ENABLED] = enabled }
    }
}
