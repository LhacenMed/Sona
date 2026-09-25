package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.playbackParentOf
import com.lhacenmed.sona.core.model.toStorageKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** Shared with [ShuffleSettings], so whether shuffle is on stays stored where it always was. */
internal val Context.playbackDataStore by preferencesDataStore(name = "playback_settings")

private val REMEMBER_PAUSE = booleanPreferencesKey("remember_pause")
private val REWIND_BEFORE_SKIP_BACK = booleanPreferencesKey("rewind_before_skip_back")
private val HEADSET_AUTOPLAY = booleanPreferencesKey("headset_autoplay")
private val REPEAT_MODE = stringPreferencesKey("repeat_mode")
private val STOP_AFTER_CURRENT_ENABLED = booleanPreferencesKey("stop_after_current_enabled")
private val PLAYBACK_PARENT = stringPreferencesKey("playback_parent")

/**
 * Audio-playback behavior settings (ported from Auxio's `PlaybackSettings`) plus restart-only
 * repeat persistence (ported from Fossify's `playbackSetting`). Shuffle has its own [ShuffleSettings].
 *
 * Auxio itself backs these with plain `SharedPreferences`; here they're stored via DataStore
 * Preferences to match the rest of Sona's settings (see [LibrarySettings] for the same pattern).
 */
@Singleton
class PlaybackSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.playbackDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    /** When true, a track-change action (skip/prev) leaves the current play/pause state alone. */
    val rememberPause: Setting<Boolean> = cache.setting { it[REMEMBER_PAUSE] ?: false }

    suspend fun setRememberPause(enabled: Boolean) {
        dataStore.edit { it[REMEMBER_PAUSE] = enabled }
    }

    /**
     * Auxio's "rewindWithPrev": when true, skip-back rewinds the current track (media3's stock
     * ~3s threshold) before jumping to the previous item; when false, skip-back always jumps to
     * the literal previous item.
     */
    val rewindBeforeSkipBack: Setting<Boolean> = cache.setting { it[REWIND_BEFORE_SKIP_BACK] ?: false }

    suspend fun setRewindBeforeSkipBack(enabled: Boolean) {
        dataStore.edit { it[REWIND_BEFORE_SKIP_BACK] = enabled }
    }

    /** Resume playback when a wired headset is plugged in. */
    val headsetAutoplay: Setting<Boolean> = cache.setting { it[HEADSET_AUTOPLAY] ?: false }

    suspend fun setHeadsetAutoplay(enabled: Boolean) {
        dataStore.edit { it[HEADSET_AUTOPLAY] = enabled }
    }

    /**
     * The collection the saved queue was played from, so the list playing when the app was last
     * closed is still the list marked as playing when it opens - Auxio persists its parent with the
     * queue for the same reason.
     */
    val playbackParent: Setting<PlaybackParent?> = cache.setting { it[PLAYBACK_PARENT]?.let(::playbackParentOf) }

    suspend fun setPlaybackParent(parent: PlaybackParent?) {
        dataStore.edit {
            if (parent == null) it.remove(PLAYBACK_PARENT) else it[PLAYBACK_PARENT] = parent.toStorageKey()
        }
    }

    val repeatMode: Setting<RepeatMode> = cache.setting { it.readRepeatMode() }

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
    val stopAfterCurrentEnabled: Setting<Boolean> = cache.setting { it.readStopAfterCurrentEnabled() }

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

private fun Preferences.readStopAfterCurrentEnabled(): Boolean = this[STOP_AFTER_CURRENT_ENABLED] ?: false
