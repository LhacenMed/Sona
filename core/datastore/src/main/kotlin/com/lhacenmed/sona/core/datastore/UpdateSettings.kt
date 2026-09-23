package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.dataStore by preferencesDataStore(name = "update_settings")

private val AUTO_PROMPT = booleanPreferencesKey("auto_prompt")

@Singleton
class UpdateSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.dataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    /**
     * Whether a found update announces itself. This governs the prompt and nothing else: the check
     * still runs, the manifest is still saved and a staged APK still resumes - turning it off only
     * means the app waits to be asked, which is what the Updates screen's manual check is for.
     */
    val autoPrompt: Setting<Boolean> = cache.setting { it[AUTO_PROMPT] ?: true }

    suspend fun setAutoPrompt(enabled: Boolean) {
        dataStore.edit { it[AUTO_PROMPT] = enabled }
    }
}
