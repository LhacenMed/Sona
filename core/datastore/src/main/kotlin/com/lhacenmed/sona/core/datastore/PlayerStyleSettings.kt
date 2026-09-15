package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.playerStyleDataStore by preferencesDataStore(name = "player_style_settings")

private val PLAYER_STYLE = stringPreferencesKey("player_style")
private val PLAYER_SLIDER_STYLE = stringPreferencesKey("player_slider_style")

/** How the player looks (ported from ArchiveTune's `PlayerDesignStyle` and `SliderStyle` preferences). */
@Singleton
class PlayerStyleSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.playerStyleDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    /** Cinematic unless changed, as ArchiveTune's player starts. */
    val playerStyle: Setting<PlayerStyle> = cache.setting { preferences ->
        preferences[PLAYER_STYLE]?.let { name -> runCatching { PlayerStyle.valueOf(name) }.getOrNull() }
            ?: PlayerStyle.CINEMATIC
    }

    suspend fun setPlayerStyle(style: PlayerStyle) {
        dataStore.edit { it[PLAYER_STYLE] = style.name }
    }

    val sliderStyle: Setting<PlayerSliderStyle> = cache.setting { preferences ->
        preferences[PLAYER_SLIDER_STYLE]?.let { name -> runCatching { PlayerSliderStyle.valueOf(name) }.getOrNull() }
            ?: PlayerSliderStyle.STANDARD
    }

    suspend fun setSliderStyle(style: PlayerSliderStyle) {
        dataStore.edit { it[PLAYER_SLIDER_STYLE] = style.name }
    }
}
