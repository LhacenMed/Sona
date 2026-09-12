package com.lhacenmed.sona.feature.equalizer

import androidx.lifecycle.ViewModel
import com.lhacenmed.sona.feature.playback.EqualizerState
import com.lhacenmed.sona.feature.playback.SonaEqualizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * The screen's handle on the process-wide [SonaEqualizer].
 *
 * It holds no state of its own: the curve lives on the audio effect for as long as playback does,
 * so this only forwards. What it is for is reach - a [com.lhacenmed.sona.core.navigation.Screen] is
 * an object and cannot be constructor-injected, and `hiltViewModel()` is how every other screen in
 * the app resolves its dependencies.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizer: SonaEqualizer,
) : ViewModel() {

    val state: StateFlow<EqualizerState?> = equalizer.state

    fun selectPreset(preset: Int) = equalizer.selectPreset(preset)

    fun setBandLevel(bandIndex: Int, levelMillibels: Int) =
        equalizer.setBandLevel(bandIndex, levelMillibels)

    fun saveCurve() = equalizer.saveCurve()
}
