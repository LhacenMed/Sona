package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import com.lhacenmed.sona.core.datastore.ShuffleSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored playback settings the [PlaybackScreen] rows are connected to. */
@HiltViewModel
class PlaybackSettingsViewModel @Inject constructor(
    private val playbackSettings: PlaybackSettings,
    private val shuffleSettings: ShuffleSettings,
) : ViewModel() {

    val rewindBeforeSkipBack: StateFlow<Boolean> = playbackSettings.rewindBeforeSkipBack.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playbackSettings.rewindBeforeSkipBack.value)

    val stopAfterCurrentEnabled: StateFlow<Boolean> = playbackSettings.stopAfterCurrentEnabled.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playbackSettings.stopAfterCurrentEnabled.value)

    val keepShuffle: StateFlow<Boolean> = shuffleSettings.keepShuffle.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), shuffleSettings.keepShuffle.value)

    val reshuffleEachTime: StateFlow<Boolean> = shuffleSettings.reshuffleEachTime.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), shuffleSettings.reshuffleEachTime.value)

    val rememberShuffleOrder: StateFlow<Boolean> = shuffleSettings.rememberOrder.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), shuffleSettings.rememberOrder.value)

    val shuffleAllButton: StateFlow<Boolean> = shuffleSettings.shuffleAllButton.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), shuffleSettings.shuffleAllButton.value)

    fun setRewindBeforeSkipBack(enabled: Boolean) {
        viewModelScope.launch { playbackSettings.setRewindBeforeSkipBack(enabled) }
    }

    fun setStopAfterCurrentEnabled(enabled: Boolean) {
        viewModelScope.launch { playbackSettings.setStopAfterCurrentEnabled(enabled) }
    }

    fun setKeepShuffle(enabled: Boolean) {
        viewModelScope.launch { shuffleSettings.setKeepShuffle(enabled) }
    }

    fun setReshuffleEachTime(enabled: Boolean) {
        viewModelScope.launch { shuffleSettings.setReshuffleEachTime(enabled) }
    }

    fun setRememberShuffleOrder(enabled: Boolean) {
        viewModelScope.launch { shuffleSettings.setRememberOrder(enabled) }
    }

    fun setShuffleAllButton(enabled: Boolean) {
        viewModelScope.launch { shuffleSettings.setShuffleAllButton(enabled) }
    }
}
