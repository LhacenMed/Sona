package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

// One string, the searches a line each, so their order is kept - a set of strings would not keep it.
private val QUERIES = stringPreferencesKey("queries")
private const val QUERY_SEPARATOR = "\n"

/**
 * What the settings were last searched for, the latest first - kept apart from the settings themselves, as what the
 * user did rather than what they chose, so a backup leaves it behind.
 */
@Singleton
class SettingsSearchHistory @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.settingsSearchHistoryDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    val queries: Setting<List<String>> = cache.setting { preferences ->
        preferences[QUERIES]?.split(QUERY_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun setQueries(queries: List<String>) {
        dataStore.edit { it[QUERIES] = queries.joinToString(QUERY_SEPARATOR) }
    }
}
