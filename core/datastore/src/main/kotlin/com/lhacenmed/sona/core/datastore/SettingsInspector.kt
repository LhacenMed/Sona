package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Every settings file read and changed as stored, whatever it holds - for the debug tools. Through the very stores the
 * app reads, so every screen follows a change at once. Nothing in a release build uses it, so nothing of it is kept there.
 */
@Singleton
class SettingsInspector @Inject constructor(@ApplicationContext private val context: Context) {

    /** Every settings file, by its name, as the values it holds by key. */
    suspend fun read(): Map<String, Map<String, Any>> =
        context.allSettingsFiles().mapValues { (_, dataStore) -> dataStore.data.first().asMap().mapKeys { (key, _) -> key.name } }

    /** Stores [value] - of the kind the setting holds - under [key] in [file]. */
    suspend fun set(file: String, key: String, value: Any) {
        context.allSettingsFiles().getValue(file).edit { it.put(key, value) }
    }

    /** Takes [key] out of [file], so the setting goes back to its default. A key is its name, whatever its kind. */
    suspend fun remove(file: String, key: String) {
        context.allSettingsFiles().getValue(file).edit { it.remove(stringPreferencesKey(key)) }
    }
}
