package com.lhacenmed.sona.feature.equalizer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.EqualizerChoices
import com.lhacenmed.sona.core.datastore.EqualizerControlMode
import com.lhacenmed.sona.core.datastore.EqualizerProfile
import com.lhacenmed.sona.core.datastore.EqualizerSelection
import com.lhacenmed.sona.core.datastore.EqualizerSettings
import com.lhacenmed.sona.feature.playback.EqualizerCapabilities
import com.lhacenmed.sona.feature.playback.SonaEqualizer
import com.lhacenmed.sona.feature.playback.resampleBandLevels
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The equalizer as the screen draws it: what is stored, with any slider still held drawn where it is held.
 * The sound's band levels are on the device's bands; [toneLevelsMb] are where each tone's slider stands.
 */
data class EqualizerUiState(
    val choices: EqualizerChoices,
    val capabilities: EqualizerCapabilities,
    val toneLevelsMb: Map<EqualizerTone, Int>,
)

/** What the screen tells the user once something they asked for has finished. */
sealed interface EqualizerMessage {
    data object ProfileSaved : EqualizerMessage
    data class ProfilesImported(val count: Int) : EqualizerMessage
    data object ImportFailed : EqualizerMessage
    data object ProfileExported : EqualizerMessage
    data object ExportFailed : EqualizerMessage
}

/**
 * The sliders held under the finger: drawn as they move, and stored - and so heard - only once let go, as
 * ArchiveTune's are. A tone is kept where it is held, too, since its bands may stop at the device's range
 * before their average reaches it.
 */
private data class EqualizerDraft(
    val bandLevelsMb: List<Int>? = null,
    val toneLevelsMb: Map<EqualizerTone, Int> = emptyMap(),
    val outputGainMb: Int? = null,
    val bassBoostStrength: Int? = null,
    val virtualizerStrength: Int? = null,
)

/** The equalizer screen's state and actions - ArchiveTune's `EqualizerViewModel`, over [EqualizerSettings]. */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val settings: EqualizerSettings,
    private val equalizer: SonaEqualizer,
) : ViewModel() {

    private val draft = MutableStateFlow(EqualizerDraft())

    private val _messages = Channel<EqualizerMessage>(Channel.BUFFERED)
    val messages: Flow<EqualizerMessage> = _messages.receiveAsFlow()

    /** Null while playback has no equalizer to shape - before anything has played, or on a device without one. */
    val state: StateFlow<EqualizerUiState?> =
        combine(settings.choices.flow, equalizer.capabilities, draft, ::uiStateOf)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                uiStateOf(settings.choices.value, equalizer.capabilities.value, draft.value),
            )

    /** The session the system's equalizer opens on, while playback has one. */
    val audioSessionId: Int? get() = equalizer.audioSessionId

    fun setEnabled(enabled: Boolean) = launch { settings.setEnabled(enabled) }

    fun setControlMode(mode: EqualizerControlMode) = launch { settings.setControlMode(mode) }

    /** [EqualizerSelection.Flat], or one of the device's presets. */
    fun applyPreset(preset: EqualizerSelection) {
        draft.value = EqualizerDraft()
        launch {
            when (preset) {
                EqualizerSelection.Flat -> equalizer.applyFlatPreset()
                is EqualizerSelection.SystemPreset -> equalizer.applySystemPreset(preset.index)
                else -> Unit
            }
        }
    }

    fun moveTone(tone: EqualizerTone, levelMb: Int) {
        val state = state.value ?: return
        draft.update {
            it.copy(
                bandLevelsMb = tone.adjust(state.choices.sound.bandLevelsMb, levelMb, state.capabilities),
                toneLevelsMb = it.toneLevelsMb + (tone to levelMb),
            )
        }
    }

    fun releaseTone(tone: EqualizerTone) {
        draft.update { it.copy(toneLevelsMb = it.toneLevelsMb - tone) }
        releaseBands()
    }

    fun moveBand(index: Int, levelMb: Int) {
        val state = state.value ?: return
        draft.update { it.copy(bandLevelsMb = state.choices.sound.bandLevelsMb.toMutableList().also { levels -> levels[index] = levelMb }) }
    }

    fun releaseBands() {
        val levels = draft.value.bandLevelsMb ?: return
        launch {
            settings.setBandLevels(levels)
            draft.update { it.copy(bandLevelsMb = null) }
        }
    }

    fun resetBands() {
        val bandCount = state.value?.capabilities?.bandCount ?: return
        draft.value = EqualizerDraft()
        launch { settings.setBandLevels(List(bandCount) { 0 }) }
    }

    fun setOutputGainEnabled(enabled: Boolean) = launch { settings.setOutputGainEnabled(enabled) }

    fun moveOutputGain(gainMb: Int) = draft.update { it.copy(outputGainMb = gainMb) }

    fun releaseOutputGain() {
        val gain = draft.value.outputGainMb ?: return
        launch {
            settings.setOutputGain(gain)
            draft.update { it.copy(outputGainMb = null) }
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) = launch { settings.setBassBoostEnabled(enabled) }

    fun moveBassBoost(strength: Int) = draft.update { it.copy(bassBoostStrength = strength) }

    fun releaseBassBoost() {
        val strength = draft.value.bassBoostStrength ?: return
        launch {
            settings.setBassBoostStrength(strength)
            draft.update { it.copy(bassBoostStrength = null) }
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) = launch { settings.setVirtualizerEnabled(enabled) }

    fun moveVirtualizer(strength: Int) = draft.update { it.copy(virtualizerStrength = strength) }

    fun releaseVirtualizer() {
        val strength = draft.value.virtualizerStrength ?: return
        launch {
            settings.setVirtualizerStrength(strength)
            draft.update { it.copy(virtualizerStrength = null) }
        }
    }

    fun setAutoHeadroomEnabled(enabled: Boolean) = launch { settings.setAutoHeadroomEnabled(enabled) }

    /** Keeps the sound as it is now - held sliders included - under [name]. */
    fun saveProfile(name: String) {
        val state = state.value ?: return
        launch {
            settings.saveProfile(name, state.capabilities.centerFrequenciesHz, state.choices.sound)
            _messages.send(EqualizerMessage.ProfileSaved)
        }
    }

    fun applyProfile(profile: EqualizerProfile) {
        draft.value = EqualizerDraft()
        launch { settings.applyProfile(profile) }
    }

    fun deleteProfile(profile: EqualizerProfile) = launch { settings.deleteProfile(profile.id) }

    fun importProfiles(uri: Uri) = launch {
        val message = runCatching { settings.importProfiles(uri) }
            .fold(onSuccess = EqualizerMessage::ProfilesImported, onFailure = { EqualizerMessage.ImportFailed })
        draft.value = EqualizerDraft()
        _messages.send(message)
    }

    fun exportProfile(uri: Uri, profile: EqualizerProfile) = launch {
        val message = runCatching { settings.exportProfile(uri, profile) }
            .fold(onSuccess = { EqualizerMessage.ProfileExported }, onFailure = { EqualizerMessage.ExportFailed })
        _messages.send(message)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun uiStateOf(choices: EqualizerChoices, capabilities: EqualizerCapabilities?, draft: EqualizerDraft): EqualizerUiState? {
        if (capabilities == null || capabilities.bandCount <= 0) return null
        val bandLevels = resampleBandLevels(draft.bandLevelsMb ?: choices.sound.bandLevelsMb, capabilities.bandCount)
        return EqualizerUiState(
            choices = choices.copy(
                sound = choices.sound.copy(
                    bandLevelsMb = bandLevels,
                    outputGainMb = draft.outputGainMb ?: choices.sound.outputGainMb,
                    bassBoostStrength = draft.bassBoostStrength ?: choices.sound.bassBoostStrength,
                    virtualizerStrength = draft.virtualizerStrength ?: choices.sound.virtualizerStrength,
                ),
            ),
            capabilities = capabilities,
            toneLevelsMb = EqualizerTone.entries.associateWith { tone ->
                draft.toneLevelsMb[tone] ?: tone.levelIn(bandLevels, capabilities)
            },
        )
    }
}
