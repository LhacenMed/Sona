package com.lhacenmed.sona.core.datastore

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

private val Context.equalizerDataStore by preferencesDataStore(name = "equalizer_settings")

private val ENABLED = booleanPreferencesKey("enabled")
private val CONTROL_MODE = stringPreferencesKey("control_mode")
private val SELECTION = stringPreferencesKey("selection")
// The key and format the earlier equalizer kept its curve in, so a curve tuned with it carries over.
private val BAND_LEVELS = stringPreferencesKey("band_levels")
private val OUTPUT_GAIN_ENABLED = booleanPreferencesKey("output_gain_enabled")
private val OUTPUT_GAIN_MB = intPreferencesKey("output_gain_mb")
private val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
private val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
private val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
private val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
private val AUTO_HEADROOM_ENABLED = booleanPreferencesKey("auto_headroom_enabled")
private val PROFILES = stringPreferencesKey("profiles")

private const val BAND_LEVEL_SEPARATOR = ","

/**
 * The equalizer, as ArchiveTune keeps it - its `EqualizerRepository`, with its defaults: sound shaping off,
 * the basic controls, every band flat.
 *
 * Read as one [EqualizerChoices]. Any change made by hand marks the sound [EqualizerSelection.Manual], as
 * ArchiveTune's does; applying a preset or a profile turns sound shaping on.
 */
@Singleton
class EqualizerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val dataStore = context.equalizerDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    val choices: Setting<EqualizerChoices> = cache.setting { preferences ->
        EqualizerChoices(
            enabled = preferences[ENABLED] ?: false,
            controlMode = preferences.enum(CONTROL_MODE, EqualizerControlMode.BASIC),
            selection = equalizerSelectionOf(preferences[SELECTION]),
            sound = EqualizerSound(
                bandLevelsMb = preferences[BAND_LEVELS].orEmpty()
                    .split(BAND_LEVEL_SEPARATOR)
                    .mapNotNull(String::toIntOrNull),
                outputGainEnabled = preferences[OUTPUT_GAIN_ENABLED] ?: false,
                outputGainMb = (preferences[OUTPUT_GAIN_MB] ?: 0).coerceIn(EqualizerSound.OUTPUT_GAIN_RANGE_MB),
                bassBoostEnabled = preferences[BASS_BOOST_ENABLED] ?: false,
                bassBoostStrength = (preferences[BASS_BOOST_STRENGTH] ?: 0).coerceIn(EqualizerSound.STRENGTH_RANGE),
                virtualizerEnabled = preferences[VIRTUALIZER_ENABLED] ?: false,
                virtualizerStrength = (preferences[VIRTUALIZER_STRENGTH] ?: 0).coerceIn(EqualizerSound.STRENGTH_RANGE),
                autoHeadroomEnabled = preferences[AUTO_HEADROOM_ENABLED] ?: false,
            ),
            profiles = decodeEqualizerProfiles(preferences[PROFILES]),
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setControlMode(mode: EqualizerControlMode) {
        dataStore.edit { it[CONTROL_MODE] = mode.name }
    }

    suspend fun setBandLevels(levelsMb: List<Int>) = editManually { it.setBandLevels(levelsMb) }

    suspend fun setOutputGainEnabled(enabled: Boolean) = editManually { it[OUTPUT_GAIN_ENABLED] = enabled }

    suspend fun setOutputGain(gainMb: Int) =
        editManually { it[OUTPUT_GAIN_MB] = gainMb.coerceIn(EqualizerSound.OUTPUT_GAIN_RANGE_MB) }

    suspend fun setBassBoostEnabled(enabled: Boolean) = editManually { it[BASS_BOOST_ENABLED] = enabled }

    suspend fun setBassBoostStrength(strength: Int) =
        editManually { it[BASS_BOOST_STRENGTH] = strength.coerceIn(EqualizerSound.STRENGTH_RANGE) }

    suspend fun setVirtualizerEnabled(enabled: Boolean) = editManually { it[VIRTUALIZER_ENABLED] = enabled }

    suspend fun setVirtualizerStrength(strength: Int) =
        editManually { it[VIRTUALIZER_STRENGTH] = strength.coerceIn(EqualizerSound.STRENGTH_RANGE) }

    suspend fun setAutoHeadroomEnabled(enabled: Boolean) = editManually { it[AUTO_HEADROOM_ENABLED] = enabled }

    /** Puts the bands at a preset's [levelsMb] - flat, or one of the device's - leaving the rest of the sound as it is. */
    suspend fun applyPreset(levelsMb: List<Int>, preset: EqualizerSelection) {
        dataStore.edit {
            it[ENABLED] = true
            it.setBandLevels(levelsMb)
            it[SELECTION] = preset.toStorageKey()
        }
    }

    suspend fun applyProfile(profile: EqualizerProfile) {
        dataStore.edit {
            it[ENABLED] = true
            it.setSound(profile.sound)
            it[SELECTION] = EqualizerSelection.Profile(profile.id).toStorageKey()
        }
    }

    /** Keeps [sound] as a new profile called [name], and selects it. */
    suspend fun saveProfile(name: String, centerFrequenciesHz: List<Int>, sound: EqualizerSound) {
        val profile = EqualizerProfile(UUID.randomUUID().toString(), name.trim(), centerFrequenciesHz, sound)
        dataStore.edit {
            it[PROFILES] = encodeEqualizerProfiles(decodeEqualizerProfiles(it[PROFILES]) + profile)
            it[SELECTION] = EqualizerSelection.Profile(profile.id).toStorageKey()
        }
    }

    /** Forgets a profile; the sound it left in place stays, as one tuned by hand. */
    suspend fun deleteProfile(id: String) {
        dataStore.edit {
            it[PROFILES] = encodeEqualizerProfiles(decodeEqualizerProfiles(it[PROFILES]).filterNot { profile -> profile.id == id })
            if (it[SELECTION] == EqualizerSelection.Profile(id).toStorageKey()) {
                it[SELECTION] = EqualizerSelection.Manual.toStorageKey()
            }
        }
    }

    /**
     * Adds the profiles in the file at [uri] and applies the first, returning how many there were. A profile
     * whose id is already taken comes in under a new one. Throws when the file holds no profile.
     */
    suspend fun importProfiles(uri: Uri): Int {
        val raw = withContext(ioDispatcher) {
            checkNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
        }
        val imported = decodeImportedEqualizerProfiles(raw)
        require(imported.isNotEmpty())
        dataStore.edit {
            val existing = decodeEqualizerProfiles(it[PROFILES])
            val takenIds = existing.mapTo(HashSet()) { profile -> profile.id }
            val added = imported.map { profile ->
                val id = profile.id.takeIf { id -> id.isNotBlank() && takenIds.add(id) }
                    ?: generateSequence { UUID.randomUUID().toString() }.first(takenIds::add)
                profile.copy(id = id)
            }
            it[PROFILES] = encodeEqualizerProfiles(existing + added)
            it[ENABLED] = true
            it.setSound(added.first().sound)
            it[SELECTION] = EqualizerSelection.Profile(added.first().id).toStorageKey()
        }
        return imported.size
    }

    /** Writes [profile] to the file at [uri], in the format [importProfiles] reads. */
    suspend fun exportProfile(uri: Uri, profile: EqualizerProfile) =
        withContext(ioDispatcher) {
            checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use {
                it.write(encodeEqualizerProfiles(listOf(profile)))
            }
        }

    private suspend fun editManually(block: (MutablePreferences) -> Unit) {
        dataStore.edit {
            block(it)
            it[SELECTION] = EqualizerSelection.Manual.toStorageKey()
        }
    }

    private fun MutablePreferences.setBandLevels(levelsMb: List<Int>) {
        this[BAND_LEVELS] = levelsMb.joinToString(BAND_LEVEL_SEPARATOR)
    }

    private fun MutablePreferences.setSound(sound: EqualizerSound) {
        setBandLevels(sound.bandLevelsMb)
        this[OUTPUT_GAIN_ENABLED] = sound.outputGainEnabled
        this[OUTPUT_GAIN_MB] = sound.outputGainMb
        this[BASS_BOOST_ENABLED] = sound.bassBoostEnabled
        this[BASS_BOOST_STRENGTH] = sound.bassBoostStrength
        this[VIRTUALIZER_ENABLED] = sound.virtualizerEnabled
        this[VIRTUALIZER_STRENGTH] = sound.virtualizerStrength
        this[AUTO_HEADROOM_ENABLED] = sound.autoHeadroomEnabled
    }
}
