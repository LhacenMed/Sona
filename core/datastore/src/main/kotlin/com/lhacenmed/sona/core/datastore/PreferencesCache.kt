package com.lhacenmed.sona.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A preferences file mirrored into memory for the life of the process.
 *
 * Read from disk once, then kept current by following the file, so every [Setting] built from it
 * reads synchronously. Writes still go straight to the DataStore and come back through here like any
 * other change - there is one source of truth, and this is only its in-memory copy.
 */
internal class PreferencesCache(dataStore: DataStore<Preferences>, scope: CoroutineScope) {

    private val preferences = MutableStateFlow<Preferences?>(null)

    init {
        scope.launch { dataStore.data.collect { preferences.value = it } }
    }

    suspend fun awaitLoaded() {
        preferences.first { it != null }
    }

    fun <T> setting(read: (Preferences) -> T): Setting<T> = Setting(preferences, read)
}
