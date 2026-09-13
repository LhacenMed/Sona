package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.PlaybackSettings
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
) : ViewModel() {

    val stopAfterCurrentEnabled: StateFlow<Boolean> = playbackSettings.stopAfterCurrentEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setStopAfterCurrentEnabled(enabled: Boolean) {
        viewModelScope.launch { playbackSettings.setStopAfterCurrentEnabled(enabled) }
    }
}
