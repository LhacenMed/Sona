package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.model.FastScrollTouchArea
import com.lhacenmed.sona.core.model.FolderView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")

// Stored as the set of *hidden* tabs (not visible ones), so a tab added later is visible to everyone
// who has chosen theirs - and a fresh datastore means [DEFAULT_HIDDEN_TABS], with no seed step.
private val HIDDEN_TABS = stringSetPreferencesKey("hidden_tabs")

/** Folders are hidden until asked for: the Videos tab and the music tabs already reach every file. */
private val DEFAULT_HIDDEN_TABS = setOf(LibraryTab.FOLDERS.name)

private val INTELLIGENT_SORTING_ENABLED = booleanPreferencesKey("intelligent_sorting_enabled")

private val FAST_SCROLL_TOUCH_AREA = stringPreferencesKey("fast_scroll_touch_area")

private val PULL_TO_REFRESH_ENABLED = booleanPreferencesKey("pull_to_refresh_enabled")

private val SEPARATE_ARTISTS = booleanPreferencesKey("separate_artists")

private val FOLDER_VIEW = stringPreferencesKey("folder_view")

private val KEEPS_MOVED_FILES = booleanPreferencesKey("keeps_moved_files")

@Singleton
class LibrarySettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.libraryDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    val excludedFolders: Setting<Set<String>> = cache.setting { it[EXCLUDED_FOLDERS] ?: emptySet() }

    /** Keeps [paths] out of the library, all in one write. */
    suspend fun addExcludedFolders(paths: Collection<String>) {
        dataStore.edit { it[EXCLUDED_FOLDERS] = (it[EXCLUDED_FOLDERS] ?: emptySet()) + paths }
    }

    /** Lets [paths] back into the library, all in one write. */
    suspend fun removeExcludedFolders(paths: Collection<String>) {
        dataStore.edit { it[EXCLUDED_FOLDERS] = (it[EXCLUDED_FOLDERS] ?: emptySet()) - paths.toSet() }
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

    /**
     * Whether a credit naming several artists - "Justin Bieber, Benny Blanco & Billie Eilish" - is read as each
     * of them, an artist of their own, rather than as one artist by that whole name.
     */
    val separateArtists: Setting<Boolean> = cache.setting { it[SEPARATE_ARTISTS] ?: true }

    suspend fun setSeparateArtists(enabled: Boolean) {
        dataStore.edit { it[SEPARATE_ARTISTS] = enabled }
    }

    /** How the Folders tab lists folders that lie inside one another. */
    val folderView: Setting<FolderView> = cache.setting { preferences ->
        preferences[FOLDER_VIEW]?.let { name -> runCatching { FolderView.valueOf(name) }.getOrNull() } ?: FolderView.SEPARATE
    }

    suspend fun setFolderView(view: FolderView) {
        dataStore.edit { it[FOLDER_VIEW] = view.name }
    }

    /**
     * Whether a track whose file moved or was renamed is known by its content - its playlists, plays and place in
     * every list kept - rather than taken out of the library with its old path and found anew at the new one.
     */
    val keepsMovedFiles: Setting<Boolean> = cache.setting { it[KEEPS_MOVED_FILES] ?: false }

    suspend fun setKeepsMovedFiles(enabled: Boolean) {
        dataStore.edit { it[KEEPS_MOVED_FILES] = enabled }
    }
}
