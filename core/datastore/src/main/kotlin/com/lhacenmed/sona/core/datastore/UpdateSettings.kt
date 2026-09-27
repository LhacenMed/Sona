package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.dataStore by preferencesDataStore(name = "update_settings")

private val CHANNEL = stringPreferencesKey("channel")
private val NOTIFICATIONS = booleanPreferencesKey("notifications")
private val AUTO_PROMPT = booleanPreferencesKey("auto_prompt")

/**
 * How the app looks for updates - ArchiveTune's `UpdateChannelKey` and `EnableUpdateNotificationKey`, and
 * Khatmah's `autoPrompt`.
 */
@Singleton
class UpdateSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.dataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    // "BETA" is what the Artifact channel was stored as before it was renamed.
    val channel: Setting<UpdateChannel> = cache.setting {
        if (it[CHANNEL] == "BETA") UpdateChannel.ARTIFACT else it.enum(CHANNEL, UpdateChannel.STABLE)
    }

    suspend fun setChannel(channel: UpdateChannel) {
        dataStore.edit { it[CHANNEL] = channel.name }
    }

    /** Whether the app looks for updates in the background, and posts a notification when it finds one. */
    val notifications: Setting<Boolean> = cache.setting { it[NOTIFICATIONS] ?: false }

    suspend fun setNotifications(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    /**
     * Whether a newer version found as the app opens is shown at once, in its sheet - Khatmah's `autoPrompt`.
     * It governs that sheet and nothing else: updates are still looked for, and checking on the Updates
     * screen still shows what it finds.
     */
    val autoPrompt: Setting<Boolean> = cache.setting { it[AUTO_PROMPT] ?: true }

    suspend fun setAutoPrompt(enabled: Boolean) {
        dataStore.edit { it[AUTO_PROMPT] = enabled }
    }
}
