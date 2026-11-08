package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.datastore.EqualizerChoices
import com.lhacenmed.sona.core.datastore.EqualizerSelection
import com.lhacenmed.sona.core.datastore.EqualizerSettings
import com.lhacenmed.sona.core.datastore.EqualizerSound
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the device's equalizer offers - read from it, never assumed: its bands, the gain each takes and its
 * own presets. ArchiveTune's `EqCapabilities`.
 */
data class EqualizerCapabilities(
    /** Each band's centre, in band order. */
    val centerFrequenciesHz: List<Int>,
    val bandLevelRangeMb: IntRange,
    val presetNames: List<String>,
) {
    val bandCount: Int get() = centerFrequenciesHz.size
}

/**
 * The equalizer, bass boost, virtualizer and loudness enhancer on Sona's own audio session, playing the
 * stored [EqualizerSettings] - ArchiveTune's, from its `MusicService`: every change to the settings is
 * applied as it is stored, whether or not a screen is open.
 *
 * [PlaybackService] attaches it to the player's session and releases it with the player. Opening the
 * session is announced, as ArchiveTune announces it, so the system's equalizer - and any other app's - can
 * shape it too.
 *
 * Its loudness enhancer also carries the volume boost - see [setVolumeBoost] - on top of the output gain the
 * settings give, the one effect for both.
 */
@Singleton
class SonaEqualizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: EqualizerSettings,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private var effects: AudioEffects? = null
    private var following: Job? = null

    /** What the settings play, as last applied - kept so the boost can be applied with them. */
    private var playing: EqualizerChoices? = null

    /** The gain the volume boost adds, in millibels. */
    private var volumeBoostMb = 0

    private val _capabilities = MutableStateFlow<EqualizerCapabilities?>(null)

    /** Null until the player's session has an equalizer, and on a device that provides none at all. */
    val capabilities: StateFlow<EqualizerCapabilities?> = _capabilities.asStateFlow()

    /** The session the effects are on, for the system's equalizer to open on - null while none is. */
    val audioSessionId: Int? get() = effects?.sessionId

    internal fun attach(audioSessionId: Int) {
        // The equalizer is what the screen is built on, so without it nothing is attached; the others are
        // each optional, as a device may lack any of them.
        val equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull() ?: return
        val capabilities = runCatching { equalizer.capabilities() }.getOrElse {
            equalizer.release()
            return
        }
        val attached = AudioEffects(
            sessionId = audioSessionId,
            equalizer = equalizer,
            bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull(),
            virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull(),
            loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull(),
        )
        effects = attached
        _capabilities.value = capabilities
        following = scope.launch {
            settings.choices.flow.collect { choices ->
                playing = choices
                attached.play(choices, capabilities, volumeBoostMb)
            }
        }
        context.sendBroadcast(sessionIntent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, audioSessionId))
    }

    internal fun release() {
        val attached = effects ?: return
        following?.cancel()
        following = null
        effects = null
        _capabilities.value = null
        attached.release()
        context.sendBroadcast(sessionIntent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, attached.sessionId))
    }

    /**
     * Plays louder than the device's own loudest by [boost], 0 to 1: evenly in decibels up to [MAX_VOLUME_BOOST_MB] -
     * what a volume past 100% reaches, up to 200%. 0 leaves the sound as the settings shape it.
     */
    fun setVolumeBoost(boost: Float) {
        volumeBoostMb = (MAX_VOLUME_BOOST_MB * boost.coerceIn(0f, 1f)).roundToInt()
        val attached = effects ?: return
        val choices = playing ?: return
        attached.play(choices, _capabilities.value ?: return, volumeBoostMb)
    }

    /** Every band at zero, and sound shaping on. */
    suspend fun applyFlatPreset() {
        val bandCount = _capabilities.value?.bandCount ?: return
        settings.applyPreset(List(bandCount) { 0 }, EqualizerSelection.Flat)
    }

    /** The device's preset at [index], read off its equalizer as levels - then stored like any other. */
    suspend fun applySystemPreset(index: Int) {
        val equalizer = effects?.equalizer ?: return
        val levels = runCatching {
            equalizer.usePreset(index.toShort())
            List(equalizer.numberOfBands.toInt()) { band -> equalizer.getBandLevel(band.toShort()).toInt() }
        }.getOrNull() ?: return
        settings.applyPreset(levels, EqualizerSelection.SystemPreset(index))
    }

    private fun sessionIntent(action: String, audioSessionId: Int) =
        Intent(action)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
}

/**
 * [levelsMb] spread over [bandCount] bands - a curve stored on a device with a different number of them,
 * read along its length. ArchiveTune's `resampleLevels`.
 */
fun resampleBandLevels(levelsMb: List<Int>, bandCount: Int): List<Int> {
    if (bandCount <= 0) return emptyList()
    if (levelsMb.isEmpty()) return List(bandCount) { 0 }
    if (levelsMb.size == bandCount) return levelsMb
    if (bandCount == 1) return listOf(levelsMb.average().toInt())
    val lastIndex = levelsMb.lastIndex.toFloat().coerceAtLeast(1f)
    return List(bandCount) { index ->
        val position = index * lastIndex / (bandCount - 1)
        val lower = floor(position).toInt().coerceIn(0, levelsMb.lastIndex)
        val upper = ceil(position).toInt().coerceIn(0, levelsMb.lastIndex)
        (levelsMb[lower] + (levelsMb[upper] - levelsMb[lower]) * (position - lower)).toInt()
    }
}

private class AudioEffects(
    val sessionId: Int,
    val equalizer: Equalizer,
    val bassBoost: BassBoost?,
    val virtualizer: Virtualizer?,
    val loudnessEnhancer: LoudnessEnhancer?,
) {

    /**
     * Puts [choices] on every effect - ArchiveTune's `applyEqSettingsToEffects` - with [boostMb] more on the
     * loudness enhancer's. Each call is guarded: the effects can be released mid-way, as the player goes, and an
     * effect a device refuses a value for keeps the rest working.
     */
    fun play(choices: EqualizerChoices, capabilities: EqualizerCapabilities, boostMb: Int) {
        val enabled = choices.enabled
        val sound = choices.sound
        val levels = resampleBandLevels(sound.bandLevelsMb, capabilities.bandCount)
        runCatching { equalizer.enabled = enabled }
        levels.forEachIndexed { band, level ->
            runCatching { equalizer.setBandLevel(band.toShort(), level.coerceIn(capabilities.bandLevelRangeMb).toShort()) }
        }
        bassBoost?.let {
            runCatching { it.enabled = enabled && sound.bassBoostEnabled }
            runCatching { it.setStrength(sound.bassBoostStrength.toShort()) }
        }
        virtualizer?.let {
            runCatching { it.enabled = enabled && sound.virtualizerEnabled }
            runCatching { it.setStrength(sound.virtualizerStrength.toShort()) }
        }
        loudnessEnhancer?.let {
            val shapesOutput = enabled && (sound.autoHeadroomEnabled || sound.outputGainEnabled)
            runCatching { it.setTargetGain((if (shapesOutput) sound.outputGainFor(levels) else 0) + boostMb) }
            runCatching { it.enabled = shapesOutput || boostMb > 0 }
        }
    }

    fun release() {
        runCatching { equalizer.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
    }
}

/** The output's gain: down by the highest boost with automatic headroom, the chosen gain otherwise. */
private fun EqualizerSound.outputGainFor(levelsMb: List<Int>): Int =
    when {
        autoHeadroomEnabled -> -(levelsMb.maxOrNull()?.coerceAtLeast(0) ?: 0)
        outputGainEnabled -> outputGainMb
        else -> 0
    }

/**
 * The most the volume boost adds, in millibels: +20 dB, where volume boosters commonly stop. The effect takes any gain,
 * squeezing what passes full scale through its own limiter - past this, so hard that the sound pumps and breaks up.
 */
private const val MAX_VOLUME_BOOST_MB = 2000f

private fun Equalizer.capabilities() = EqualizerCapabilities(
    // The platform reports centre frequencies in millihertz.
    centerFrequenciesHz = List(numberOfBands.toInt()) { band -> getCenterFreq(band.toShort()) / 1000 },
    bandLevelRangeMb = bandLevelRange[0].toInt()..bandLevelRange[1].toInt(),
    presetNames = List(numberOfPresets.toInt()) { preset -> getPresetName(preset.toShort()) },
)
