package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.quickplay.QuickPlaySource
import com.lhacenmed.sona.core.data.quickplay.QuickPlaySourceRepository
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import com.lhacenmed.sona.core.datastore.QuickPlayMode
import com.lhacenmed.sona.core.datastore.QuickPlaySettings
import com.lhacenmed.sona.core.datastore.ShuffleSettings
import com.lhacenmed.sona.core.model.PlaybackParent
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
    private val quickPlaySettings: QuickPlaySettings,
    private val quickPlaySources: QuickPlaySourceRepository,
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

    val showQuickPlayButton: StateFlow<Boolean> = quickPlaySettings.showButton.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), quickPlaySettings.showButton.value)

    val quickPlayMode: StateFlow<QuickPlayMode> = quickPlaySettings.mode.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), quickPlaySettings.mode.value)

    val quickPlaySource: StateFlow<QuickPlaySource> = quickPlaySources.source

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

    fun setShowQuickPlayButton(enabled: Boolean) {
        viewModelScope.launch { quickPlaySettings.setShowButton(enabled) }
    }

    fun setQuickPlayMode(mode: QuickPlayMode) {
        viewModelScope.launch { quickPlaySettings.setMode(mode) }
    }

    /**
     * Makes [parent] - every track while null, or a listening history - what quick play plays: the sources
     * chosen right here. A collection is chosen on its own picker instead.
     */
    fun chooseQuickPlaySource(parent: PlaybackParent?) {
        quickPlaySources.choose(parent)
    }
}
