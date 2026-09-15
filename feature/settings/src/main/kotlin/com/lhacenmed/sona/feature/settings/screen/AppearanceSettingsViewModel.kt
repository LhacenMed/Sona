package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.datastore.PlayerStyleSettings
import com.lhacenmed.sona.core.datastore.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored theme and player style settings the [AppearanceScreen] rows are connected to. */
@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val themeSettings: ThemeSettings,
    private val playerStyleSettings: PlayerStyleSettings,
) : ViewModel() {

    val roundMode: StateFlow<Boolean> = themeSettings.roundMode.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), themeSettings.roundMode.value)

    val playerStyle: StateFlow<PlayerStyle> = playerStyleSettings.playerStyle.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playerStyleSettings.playerStyle.value)

    val sliderStyle: StateFlow<PlayerSliderStyle> = playerStyleSettings.sliderStyle.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playerStyleSettings.sliderStyle.value)

    fun setRoundMode(enabled: Boolean) {
        viewModelScope.launch { themeSettings.setRoundMode(enabled) }
    }

    fun setPlayerStyle(style: PlayerStyle) {
        viewModelScope.launch { playerStyleSettings.setPlayerStyle(style) }
    }

    fun setSliderStyle(style: PlayerSliderStyle) {
        viewModelScope.launch { playerStyleSettings.setSliderStyle(style) }
    }
}
