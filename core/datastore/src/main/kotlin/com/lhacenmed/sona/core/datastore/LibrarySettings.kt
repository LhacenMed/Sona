package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "library_settings")

private val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")

// Stored as the set of *hidden* tabs (not visible ones) so that an empty/fresh datastore already
// means "every tab visible" - no seed/migration step needed for the default state.
private val HIDDEN_TABS = stringSetPreferencesKey("hidden_tabs")

private val INTELLIGENT_SORTING_ENABLED = booleanPreferencesKey("intelligent_sorting_enabled")

@Singleton
class LibrarySettings @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.dataStore

    val excludedFolders: Flow<Set<String>> =
        dataStore.data.map { it[EXCLUDED_FOLDERS] ?: emptySet() }

    suspend fun addExcludedFolder(path: String) {
        dataStore.edit { it[EXCLUDED_FOLDERS] = (it[EXCLUDED_FOLDERS] ?: emptySet()) + path }
    }

    suspend fun removeExcludedFolder(path: String) {
        dataStore.edit { it[EXCLUDED_FOLDERS] = (it[EXCLUDED_FOLDERS] ?: emptySet()) - path }
    }

    val visibleTabs: Flow<Set<LibraryTab>> = dataStore.data.map { preferences ->
        val hidden = preferences[HIDDEN_TABS].orEmpty().mapNotNullTo(mutableSetOf()) { name ->
            runCatching { LibraryTab.valueOf(name) }.getOrNull()
        }
        LibraryTab.entries.toSet() - hidden
    }

    suspend fun setTabVisible(tab: LibraryTab, visible: Boolean) {
        dataStore.edit { preferences ->
            val hidden = preferences[HIDDEN_TABS].orEmpty().toMutableSet()
            if (visible) hidden.remove(tab.name) else hidden.add(tab.name)
            preferences[HIDDEN_TABS] = hidden
        }
    }

    val intelligentSortingEnabled: Flow<Boolean> =
        dataStore.data.map { it[INTELLIGENT_SORTING_ENABLED] ?: true }

    suspend fun setIntelligentSortingEnabled(enabled: Boolean) {
        dataStore.edit { it[INTELLIGENT_SORTING_ENABLED] = enabled }
    }
}
