package com.lhacenmed.sona.feature.playback

import android.media.audiofx.Equalizer
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.datastore.EqualizerSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The preset index standing for "no device preset" - a curve the user tuned by hand. */
const val CUSTOM_PRESET = -1

/** One band of the device's equalizer: where it sits, and how much it is currently boosting. */
data class EqualizerBand(val centerFrequencyHz: Int, val levelMillibels: Int)

/**
 * Everything the equalizer screen draws, read from the device rather than assumed: band count,
 * centre frequencies, the gain range and the preset names all belong to whichever equalizer
 * implementation the device ships.
 */
data class EqualizerState(
    val bands: List<EqualizerBand>,
    val levelRangeMillibels: IntRange,
    val presetNames: List<String>,
    val selectedPreset: Int,
)

/**
 * The platform equalizer attached to Sona's own audio session.
 *
 * Ported from Fossify's `SimpleEqualizer`, which is a global `object` holding a `lateinit` effect:
 * because nothing could observe it becoming ready, its equalizer screen polled for the `lateinit`
 * five times at 100 ms intervals before giving up. Here the effect is a singleton on the object
 * graph and publishes [state], so a screen waits on the state it needs rather than for a field to
 * appear - and there is nothing left to poll.
 *
 * [PlaybackService] owns the lifecycle, so the curve keeps applying while the UI is closed.
 */
@Singleton
class SonaEqualizer @Inject constructor(
    private val settings: EqualizerSettings,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private var equalizer: Equalizer? = null

    private val _state = MutableStateFlow<EqualizerState?>(null)

    /** Null until the player's session has one, and on devices that provide no equalizer at all. */
    val state: StateFlow<EqualizerState?> = _state.asStateFlow()

    /**
     * Binds to [audioSessionId] and restores the stored curve onto it.
     *
     * Priority 0 and the player's own session, matching Fossify: the curve shapes Sona's output
     * rather than the device's whole audio mix.
     */
    internal fun attach(audioSessionId: Int) {
        scope.launch {
            val storedPreset = settings.preset.first()
            val storedLevels = settings.bandLevels.first()
            val engine = runCatching { Equalizer(0, audioSessionId).apply { enabled = true } }
                .getOrNull() ?: return@launch
            equalizer = engine
            _state.value = engine.applyCurve(storedPreset, storedLevels)
        }
    }

    internal fun release() {
        equalizer?.release()
        equalizer = null
        _state.value = null
    }

    /** Puts a device preset - or the stored hand-tuned curve, for [CUSTOM_PRESET] - on the engine. */
    fun selectPreset(preset: Int) {
        val engine = equalizer ?: return
        scope.launch {
            _state.value = engine.applyCurve(preset, settings.bandLevels.first())
            settings.setPreset(preset)
        }
    }

    /**
     * Moves one band, which by definition makes the curve a custom one.
     *
     * Nothing is written here: a drag produces a level per frame, and the curve is only worth
     * persisting once the user lets go. [saveCurve] is what the screen calls then.
     */
    fun setBandLevel(bandIndex: Int, levelMillibels: Int) {
        val engine = equalizer ?: return
        engine.setBandLevel(bandIndex.toShort(), levelMillibels.toShort())
        _state.update { current ->
            current?.copy(
                bands = current.bands.mapIndexed { index, band ->
                    if (index == bandIndex) band.copy(levelMillibels = levelMillibels) else band
                },
                selectedPreset = CUSTOM_PRESET,
            )
        }
    }

    /** Remembers the curve the bands are currently at, so it is restored on the next launch. */
    fun saveCurve() {
        val bands = _state.value?.bands ?: return
        scope.launch {
            settings.setPreset(CUSTOM_PRESET)
            settings.setBandLevels(bands.map { it.levelMillibels })
        }
    }
}

private fun Equalizer.applyCurve(preset: Int, customLevels: List<Int>): EqualizerState {
    // A preset index stored on another device may not exist on this one; the hand-tuned curve is
    // the fallback, the same one Fossify drops to whenever a preset fails to apply.
    val resolvedPreset = preset.takeIf { it in 0 until numberOfPresets.toInt() } ?: CUSTOM_PRESET
    if (resolvedPreset != CUSTOM_PRESET) {
        usePreset(resolvedPreset.toShort())
    } else if (customLevels.size == numberOfBands.toInt()) {
        // A curve saved against a different band count cannot be mapped onto this device's bands,
        // so the engine is left at whatever it already has rather than half-restored.
        customLevels.forEachIndexed { index, level -> setBandLevel(index.toShort(), level.toShort()) }
    }
    return readState(resolvedPreset)
}

private fun Equalizer.readState(preset: Int) = EqualizerState(
    bands = (0 until numberOfBands.toInt()).map { band ->
        EqualizerBand(
            // The platform reports centre frequencies in millihertz.
            centerFrequencyHz = getCenterFreq(band.toShort()) / 1000,
            levelMillibels = getBandLevel(band.toShort()).toInt(),
        )
    },
    levelRangeMillibels = bandLevelRange[0].toInt()..bandLevelRange[1].toInt(),
    presetNames = (0 until numberOfPresets.toInt()).map { getPresetName(it.toShort()) },
    selectedPreset = preset,
)
