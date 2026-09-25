package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.EffectSettings
import com.lhacenmed.sona.core.datastore.LibrarySettings
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
) : ViewModel() {

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
}
