package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
private val KEEP_SHUFFLE = booleanPreferencesKey("keep_shuffle")
private val RESHUFFLE_EACH_TIME = booleanPreferencesKey("reshuffle_each_time")
private val REMEMBER_SHUFFLE_ORDER = booleanPreferencesKey("remember_shuffle_order")
private val SHUFFLE_ALL_BUTTON = booleanPreferencesKey("shuffle_all_button")

/**
 * Everything about shuffle the user can choose, in one place: whether it is on, and how it behaves.
 *
 * Stored in the playback settings file, where whether shuffle is on has always been kept.
 */
@Singleton
class ShuffleSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.playbackDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    /** Whether the queue plays shuffled, kept across restarts - Fossify's `isShuffleEnabled`. */
    val enabled: Setting<Boolean> = cache.setting { it[SHUFFLE_ENABLED] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[SHUFFLE_ENABLED] = enabled }
    }

    /**
     * Whether playing a track from a list keeps shuffle as it is - Auxio's `keepShuffle`. When off, a
     * tapped track always starts its list in order; Play and Shuffle still say what they mean.
     */
    val keepShuffle: Setting<Boolean> = cache.setting { it[KEEP_SHUFFLE] ?: true }

    suspend fun setKeepShuffle(enabled: Boolean) {
        dataStore.edit { it[KEEP_SHUFFLE] = enabled }
    }

    /**
     * Whether turning shuffle on deals a new order from the track playing - what Auxio, ArchiveTune and
     * Fossify do. When off, a queue keeps the order it was dealt when it was set, so turning shuffle
     * off and on again brings back the same order.
     */
    val reshuffleEachTime: Setting<Boolean> = cache.setting { it[RESHUFFLE_EACH_TIME] ?: false }

    suspend fun setReshuffleEachTime(enabled: Boolean) {
        dataStore.edit { it[RESHUFFLE_EACH_TIME] = enabled }
    }

    /**
     * Whether a queue restored after the app was closed plays on in the order it was shuffled - Auxio
     * persists its shuffled mapping. When off, it is dealt again from the track it stopped on.
     */
    val rememberOrder: Setting<Boolean> = cache.setting { it[REMEMBER_SHUFFLE_ORDER] ?: true }

    suspend fun setRememberOrder(enabled: Boolean) {
        dataStore.edit { it[REMEMBER_SHUFFLE_ORDER] = enabled }
    }

    /** Whether the library shows its button for shuffling every track - Auxio's home shuffle FAB. */
    val shuffleAllButton: Setting<Boolean> = cache.setting { it[SHUFFLE_ALL_BUTTON] ?: true }

    suspend fun setShuffleAllButton(enabled: Boolean) {
        dataStore.edit { it[SHUFFLE_ALL_BUTTON] = enabled }
    }
}
