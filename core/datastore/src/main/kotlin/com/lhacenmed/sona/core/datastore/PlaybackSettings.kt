package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.model.RepeatMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playbackDataStore by preferencesDataStore(name = "playback_settings")

private val REMEMBER_PAUSE = booleanPreferencesKey("remember_pause")
private val REWIND_BEFORE_SKIP_BACK = booleanPreferencesKey("rewind_before_skip_back")
private val HEADSET_AUTOPLAY = booleanPreferencesKey("headset_autoplay")
private val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
private val REPEAT_MODE = stringPreferencesKey("repeat_mode")
private val STOP_AFTER_CURRENT_ENABLED = booleanPreferencesKey("stop_after_current_enabled")

/**
 * Audio-playback behavior settings (ported from Auxio's `PlaybackSettings`) plus restart-only
 * shuffle/repeat persistence (ported from Fossify's `Config.isShuffleEnabled`/`playbackSetting`).
 *
 * Auxio itself backs these with plain `SharedPreferences`; here they're stored via DataStore
 * Preferences to match the rest of Sona's settings (see [LibrarySettings] for the same pattern).
 */
@Singleton
class PlaybackSettings @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.playbackDataStore

    /** When true, a track-change action (skip/prev) leaves the current play/pause state alone. */
    val rememberPause: Flow<Boolean> = dataStore.data.map { it[REMEMBER_PAUSE] ?: false }

    suspend fun setRememberPause(enabled: Boolean) {
        dataStore.edit { it[REMEMBER_PAUSE] = enabled }
    }

    /**
     * Auxio's "rewindWithPrev": when true, skip-back rewinds the current track (media3's stock
     * ~3s threshold) before jumping to the previous item; when false, skip-back always jumps to
     * the literal previous item.
     */
    val rewindBeforeSkipBack: Flow<Boolean> = dataStore.data.map { it[REWIND_BEFORE_SKIP_BACK] ?: true }

    suspend fun setRewindBeforeSkipBack(enabled: Boolean) {
        dataStore.edit { it[REWIND_BEFORE_SKIP_BACK] = enabled }
    }

    /** Resume playback when a wired headset is plugged in. */
    val headsetAutoplay: Flow<Boolean> = dataStore.data.map { it[HEADSET_AUTOPLAY] ?: false }

    suspend fun setHeadsetAutoplay(enabled: Boolean) {
        dataStore.edit { it[HEADSET_AUTOPLAY] = enabled }
    }

    /** Restart-persistence only - not the "remember shuffle across songs" personalize behavior. */
    val shuffleEnabled: Flow<Boolean> = dataStore.data.map { it[SHUFFLE_ENABLED] ?: false }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        dataStore.edit { it[SHUFFLE_ENABLED] = enabled }
    }

    val repeatMode: Flow<RepeatMode> = dataStore.data.map { it.readRepeatMode() }

    /**
     * Moves the repeat mode one step along the repeat button's cycle.
     *
     * Read and written in a single edit, so presses from the player and the notification each step
     * from the mode the previous press left behind, never from a stale copy of it.
     */
    suspend fun cycleRepeatMode() {
        dataStore.edit {
            it[REPEAT_MODE] = it.readRepeatMode().next(it.readStopAfterCurrentEnabled()).name
        }
    }

    /** Whether [RepeatMode.STOP_AFTER_CURRENT] is one of the modes the repeat button cycles through. */
    val stopAfterCurrentEnabled: Flow<Boolean> = dataStore.data.map { it.readStopAfterCurrentEnabled() }

    suspend fun setStopAfterCurrentEnabled(enabled: Boolean) {
        dataStore.edit {
            it[STOP_AFTER_CURRENT_ENABLED] = enabled
            // Dropping the option while it is the active mode would strand the player in a mode the
            // button can no longer reach.
            if (!enabled && it.readRepeatMode() == RepeatMode.STOP_AFTER_CURRENT) {
                it[REPEAT_MODE] = RepeatMode.OFF.name
            }
        }
    }
}

private fun Preferences.readRepeatMode(): RepeatMode =
    this[REPEAT_MODE]?.let { name -> runCatching { RepeatMode.valueOf(name) }.getOrNull() }
        ?: RepeatMode.OFF

private fun Preferences.readStopAfterCurrentEnabled(): Boolean = this[STOP_AFTER_CURRENT_ENABLED] ?: true
