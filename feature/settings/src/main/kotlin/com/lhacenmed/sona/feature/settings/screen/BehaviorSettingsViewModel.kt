package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.datastore.EffectSettings
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import com.lhacenmed.sona.core.model.FastScrollTouchArea
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored settings the [BehaviorScreen] rows are connected to. */
@HiltViewModel
class BehaviorSettingsViewModel @Inject constructor(
    private val librarySettings: LibrarySettings,
    private val effectSettings: EffectSettings,
    private val playbackSettings: PlaybackSettings,
    private val repository: LibraryRepository,
) : ViewModel() {

    val blockScreenshots: StateFlow<Boolean> = effectSettings.blockScreenshots.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), effectSettings.blockScreenshots.value)

    fun setBlockScreenshots(enabled: Boolean) {
        viewModelScope.launch { effectSettings.setBlockScreenshots(enabled) }
    }

    val pausesListenHistory: StateFlow<Boolean> = playbackSettings.pausesListenHistory.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playbackSettings.pausesListenHistory.value)

    fun setPausesListenHistory(enabled: Boolean) {
        viewModelScope.launch { playbackSettings.setPausesListenHistory(enabled) }
    }

    fun clearListenHistory(onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) { repository.clearListenHistory() }
    }

    val hapticsEnabled: StateFlow<Boolean> = effectSettings.hapticsEnabled.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), effectSettings.hapticsEnabled.value)

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { effectSettings.setHapticsEnabled(enabled) }
    }

    val fastScrollTouchArea: StateFlow<FastScrollTouchArea> = librarySettings.fastScrollTouchArea.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), librarySettings.fastScrollTouchArea.value)

    fun setFastScrollTouchArea(touchArea: FastScrollTouchArea) {
        viewModelScope.launch { librarySettings.setFastScrollTouchArea(touchArea) }
    }

    val pullToRefreshEnabled: StateFlow<Boolean> = librarySettings.pullToRefreshEnabled.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), librarySettings.pullToRefreshEnabled.value)

    fun setPullToRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch { librarySettings.setPullToRefreshEnabled(enabled) }
    }
}
