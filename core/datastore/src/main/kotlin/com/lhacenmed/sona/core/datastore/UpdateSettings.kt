package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val CHANNEL = stringPreferencesKey("channel")
private val NOTIFICATIONS = booleanPreferencesKey("notifications")
private val AUTO_PROMPT = booleanPreferencesKey("auto_prompt")
private val AUTO_UPDATE = booleanPreferencesKey("auto_update")
private val USES_MOBILE_DATA = booleanPreferencesKey("uses_mobile_data")

/**
 * How the app looks for updates - ArchiveTune's `UpdateChannelKey` and `EnableUpdateNotificationKey`, and
 * Khatmah's `autoPrompt`.
 */
@Singleton
class UpdateSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.updateDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    // "BETA" is what the Artifact channel was stored as before it was renamed.
    val channel: Setting<UpdateChannel> = cache.setting {
        if (it[CHANNEL] == "BETA") UpdateChannel.ARTIFACT else it.enum(CHANNEL, UpdateChannel.Default)
    }

    suspend fun setChannel(channel: UpdateChannel) {
        dataStore.edit { it[CHANNEL] = channel.name }
    }

    /**
     * Whether the app updates itself: a newer version is downloaded in the background and installed while the
     * app is not in use, with nothing shown of either.
     */
    val autoUpdate: Setting<Boolean> = cache.setting { it[AUTO_UPDATE] ?: true }

    suspend fun setAutoUpdate(enabled: Boolean) {
        dataStore.edit { it[AUTO_UPDATE] = enabled }
    }

    /** Whether an update is downloaded by itself on mobile data too, rather than waiting for Wi-Fi. */
    val usesMobileData: Setting<Boolean> = cache.setting { it[USES_MOBILE_DATA] ?: false }

    suspend fun setUsesMobileData(uses: Boolean) {
        dataStore.edit { it[USES_MOBILE_DATA] = uses }
    }

    /** Whether the app posts a notification when it finds an update it does not install by itself. */
    val notifications: Setting<Boolean> = cache.setting { it[NOTIFICATIONS] ?: false }

    suspend fun setNotifications(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    /**
     * Whether a newer version found as the app opens is shown at once, in its sheet - Khatmah's `autoPrompt`.
     * It governs that sheet and nothing else: updates are still looked for, and checking on the Updates
     * screen still shows what it finds. Off until asked for: the app updates itself without it.
     */
    val autoPrompt: Setting<Boolean> = cache.setting { it[AUTO_PROMPT] ?: false }

    suspend fun setAutoPrompt(enabled: Boolean) {
        dataStore.edit { it[AUTO_PROMPT] = enabled }
    }
}
