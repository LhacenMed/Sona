package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lhacenmed.sona.core.model.sort.SortableList
import com.lhacenmed.sona.core.model.withRenamedPlaylist
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Every setting the user chose, read out for a backup and put back from one.
 *
 * Read as each file holds it - the values by their key's name - so a setting added later is carried without
 * anything here knowing of it. Each value is a Boolean, Int, Long, Float, String or set of Strings: every kind
 * the settings files hold.
 */
@Singleton
class SettingsBackup @Inject constructor(@ApplicationContext private val context: Context) {

    /** Every backed-up settings file, by its name, as the values it holds by key. */
    suspend fun read(): Map<String, Map<String, Any>> =
        context.backedUpSettingsFiles().mapValues { (_, dataStore) ->
            dataStore.data.first().asMap().mapKeys { (key, _) -> key.name }
        }

    /**
     * Puts each of [files] in place of what that file holds; a file the backup does not have is left as it is.
     * A playlist named by its id - in a key or in a value - is renamed through [playlistIds], to the id it has
     * on this device. Every screen follows the files, so each change shows as it lands.
     */
    suspend fun restore(files: Map<String, Map<String, Any>>, playlistIds: Map<Long, Long>) {
        context.backedUpSettingsFiles().forEach { (name, dataStore) ->
            val values = files[name] ?: return@forEach
            dataStore.edit { preferences ->
                preferences.clear()
                values.forEach { (key, value) ->
                    preferences.put(key.withRenamedPlaylistSort(playlistIds), value.withRenamedPlaylists(playlistIds))
                }
            }
        }
    }
}

private val PLAYLIST_SORT_PREFIX = SortableList.PLAYLIST_TRACKS.instanceKeyPrefix()

/** A playlist's own sort, kept under its id, moved to the id it has now. */
private fun String.withRenamedPlaylistSort(playlistIds: Map<Long, Long>): String {
    val playlistId = takeIf { it.startsWith(PLAYLIST_SORT_PREFIX) }?.removePrefix(PLAYLIST_SORT_PREFIX)?.toLongOrNull()
    val renamedId = playlistId?.let(playlistIds::get) ?: return this
    return "$PLAYLIST_SORT_PREFIX$renamedId"
}

/** A playing list kept by its storage key - the one playing, quick play's, those not counted - renamed. */
private fun Any.withRenamedPlaylists(playlistIds: Map<Long, Long>): Any = when (this) {
    is String -> withRenamedPlaylist(this, playlistIds)
    is Set<*> -> mapTo(mutableSetOf()) { withRenamedPlaylist(it as String, playlistIds) }
    else -> this
}

@Suppress("UNCHECKED_CAST")
private fun MutablePreferences.put(name: String, value: Any) {
    when (value) {
        is Boolean -> set(booleanPreferencesKey(name), value)
        is Int -> set(intPreferencesKey(name), value)
        is Long -> set(longPreferencesKey(name), value)
        is Float -> set(floatPreferencesKey(name), value)
        is String -> set(stringPreferencesKey(name), value)
        is Set<*> -> set(stringSetPreferencesKey(name), value as Set<String>)
        else -> error("A setting of type ${value::class.simpleName} cannot be stored")
    }
}
