package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.equalizerDataStore by preferencesDataStore(name = "equalizer_settings")

private val PRESET = intPreferencesKey("preset")
private val BAND_LEVELS = stringPreferencesKey("band_levels")

private const val BAND_LEVEL_SEPARATOR = ","

/**
 * The equalizer curve, remembered across restarts (ported from Fossify's `EQUALIZER_PRESET` /
 * `EQUALIZER_BANDS`, stored via DataStore Preferences here to match the rest of Sona's settings -
 * see [PlaybackSettings] for the same pattern).
 *
 * Fossify serialises its bands as a Gson `HashMap<Short, Int>` of *seek bar progress*, which only
 * means anything alongside the device's own minimum level. These are absolute gains instead, so the
 * stored curve is self-describing and needs no JSON library to read back.
 */
@Singleton
class EqualizerSettings @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.equalizerDataStore

    /**
     * The index of the device preset in use. Defaults to the device's first preset, matching
     * Fossify; a hand-tuned curve is stored as the custom sentinel the playback layer defines.
     */
    val preset: Flow<Int> = dataStore.data.map { it[PRESET] ?: 0 }

    suspend fun setPreset(preset: Int) {
        dataStore.edit { it[PRESET] = preset }
    }

    /** Per-band gains in millibels, ordered by band index. Empty until a band is first moved. */
    val bandLevels: Flow<List<Int>> = dataStore.data.map { preferences ->
        preferences[BAND_LEVELS]
            .orEmpty()
            .split(BAND_LEVEL_SEPARATOR)
            .mapNotNull(String::toIntOrNull)
    }

    suspend fun setBandLevels(levels: List<Int>) {
        dataStore.edit { it[BAND_LEVELS] = levels.joinToString(BAND_LEVEL_SEPARATOR) }
    }
}
