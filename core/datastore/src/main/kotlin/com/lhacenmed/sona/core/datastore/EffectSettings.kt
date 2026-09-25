package com.lhacenmed.sona.core.datastore

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.effectDataStore by preferencesDataStore(name = "effect_settings")

private val DISABLE_ANIMATIONS = booleanPreferencesKey("disable_animations")
private val FORCE_HIGH_REFRESH_RATE = booleanPreferencesKey("force_high_refresh_rate")
private val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")

/**
 * How the app moves and feels across every screen - its animations, the display's refresh rate and
 * its haptics - ported from ArchiveTune's `DisableAnimationsKey`, `ForceHighRefreshRateKey` and
 * `EnableHapticFeedbackKey`, with their defaults.
 */
@Singleton
class EffectSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.effectDataStore
    private val cache = PreferencesCache(dataStore, scope)

    /** ArchiveTune's default: animations start off on a device the system itself calls low on memory. */
    private val isLowRamDevice = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    val disableAnimations: Setting<Boolean> = cache.setting { it[DISABLE_ANIMATIONS] ?: isLowRamDevice }

    suspend fun setDisableAnimations(enabled: Boolean) {
        dataStore.edit { it[DISABLE_ANIMATIONS] = enabled }
    }

    val forceHighRefreshRate: Setting<Boolean> = cache.setting { it[FORCE_HIGH_REFRESH_RATE] ?: false }

    suspend fun setForceHighRefreshRate(enabled: Boolean) {
        dataStore.edit { it[FORCE_HIGH_REFRESH_RATE] = enabled }
    }

    val hapticsEnabled: Setting<Boolean> = cache.setting { it[HAPTICS_ENABLED] ?: true }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[HAPTICS_ENABLED] = enabled }
    }
}
