package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.SubtitleBackdrop
import com.lhacenmed.sona.core.datastore.SubtitlePosition
import com.lhacenmed.sona.core.datastore.SubtitleTextSize
import com.lhacenmed.sona.core.datastore.VideoSettings
import com.lhacenmed.sona.core.datastore.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The stored video settings the [VideoScreen] rows are connected to. */
@HiltViewModel
class VideoSettingsViewModel @Inject constructor(
    private val videoSettings: VideoSettings,
) : ViewModel() {

    val keepAspect: StateFlow<Boolean> = videoSettings.keepAspect.stateIn(viewModelScope)
    val showClock: StateFlow<Boolean> = videoSettings.showClock.stateIn(viewModelScope)
    val resume: StateFlow<Boolean> = videoSettings.resume.stateIn(viewModelScope)
    val gestures: StateFlow<Boolean> = videoSettings.gestures.stateIn(viewModelScope)
    val doubleTapSeek: StateFlow<Boolean> = videoSettings.doubleTapSeek.stateIn(viewModelScope)
    val doubleTapSeekSeconds: StateFlow<Int> = videoSettings.doubleTapSeekSeconds.stateIn(viewModelScope)
    val longPressSpeedUp: StateFlow<Boolean> = videoSettings.longPressSpeedUp.stateIn(viewModelScope)
    val longPressSpeed: StateFlow<Float> = videoSettings.longPressSpeed.stateIn(viewModelScope)
    val longPressVibration: StateFlow<Boolean> = videoSettings.longPressVibration.stateIn(viewModelScope)
    val zoomPan: StateFlow<Boolean> = videoSettings.zoomPan.stateIn(viewModelScope)
    val subtitleCustomization: StateFlow<Boolean> = videoSettings.subtitleCustomization.stateIn(viewModelScope)
    val subtitleTextSize: StateFlow<SubtitleTextSize> = videoSettings.subtitleTextSize.stateIn(viewModelScope)
    val subtitleBackdrop: StateFlow<SubtitleBackdrop> = videoSettings.subtitleBackdrop.stateIn(viewModelScope)
    val subtitlePosition: StateFlow<SubtitlePosition> = videoSettings.subtitlePosition.stateIn(viewModelScope)

    fun setKeepAspect(enabled: Boolean) = update { setKeepAspect(enabled) }

    fun setShowClock(enabled: Boolean) = update { setShowClock(enabled) }

    fun setResume(enabled: Boolean) = update { setResume(enabled) }

    fun setGestures(enabled: Boolean) = update { setGestures(enabled) }


    fun setDoubleTapSeek(enabled: Boolean) = update { setDoubleTapSeek(enabled) }

    fun setDoubleTapSeekSeconds(seconds: Int) = update { setDoubleTapSeekSeconds(seconds) }

    fun setLongPressSpeedUp(enabled: Boolean) = update { setLongPressSpeedUp(enabled) }

    fun setLongPressSpeed(speed: Float) = update { setLongPressSpeed(speed) }

    fun setLongPressVibration(enabled: Boolean) = update { setLongPressVibration(enabled) }

    fun setZoomPan(enabled: Boolean) = update { setZoomPan(enabled) }

    fun setSubtitleCustomization(enabled: Boolean) = update { setSubtitleCustomization(enabled) }

    fun setSubtitleTextSize(size: SubtitleTextSize) = update { setSubtitleTextSize(size) }

    fun setSubtitleBackdrop(backdrop: SubtitleBackdrop) = update { setSubtitleBackdrop(backdrop) }

    fun setSubtitlePosition(position: SubtitlePosition) = update { setSubtitlePosition(position) }

    private fun update(write: suspend VideoSettings.() -> Unit) {
        viewModelScope.launch { videoSettings.write() }
    }
}
