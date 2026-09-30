package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.playbackParentOf
import com.lhacenmed.sona.core.model.toStorageKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

// Under the names they had as shuffle-all's, so what was chosen then is still chosen.
private val QUICK_PLAY_BUTTON = booleanPreferencesKey("shuffle_all_button")
private val QUICK_PLAY_SOURCE = stringPreferencesKey("shuffle_all_source")
private val QUICK_PLAY_MODE = stringPreferencesKey("quick_play_mode")

/** How quick play plays its source. */
enum class QuickPlayMode {
    /** From a track picked at random, shuffled - Auxio's shuffle-all. */
    SHUFFLE,

    /** From its first track, in the order its own list shows them. */
    PLAY,
    ;

    companion object {
        /** What it is until the user chooses otherwise. */
        val Default = SHUFFLE
    }
}

/**
 * Quick play: the library's button and the launcher shortcut, which both play one chosen source in
 * one chosen way - whether the button shows, what it plays from, and how.
 *
 * Stored in the playback settings file, where shuffle-all's choices have always been kept.
 */
@Singleton
class QuickPlaySettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.playbackDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    /** Whether the library shows the quick play button - Auxio's home shuffle FAB. */
    val showButton: Setting<Boolean> = cache.setting { it[QUICK_PLAY_BUTTON] ?: true }

    suspend fun setShowButton(enabled: Boolean) {
        dataStore.edit { it[QUICK_PLAY_BUTTON] = enabled }
    }

    /**
     * The collection quick play plays, or null for every track - written down as a [PlaybackParent] is,
     * so it reads back as the same collection after a rescan.
     */
    val source: Setting<PlaybackParent?> = cache.setting { it[QUICK_PLAY_SOURCE]?.let(::playbackParentOf) }

    suspend fun setSource(source: PlaybackParent?) {
        dataStore.edit {
            if (source == null) it.remove(QUICK_PLAY_SOURCE) else it[QUICK_PLAY_SOURCE] = source.toStorageKey()
        }
    }

    val mode: Setting<QuickPlayMode> = cache.setting { it.readMode() }

    suspend fun setMode(mode: QuickPlayMode) {
        dataStore.edit { it[QUICK_PLAY_MODE] = mode.name }
    }
}

private fun Preferences.readMode(): QuickPlayMode =
    this[QUICK_PLAY_MODE]?.let { name -> runCatching { QuickPlayMode.valueOf(name) }.getOrNull() }
        ?: QuickPlayMode.Default
