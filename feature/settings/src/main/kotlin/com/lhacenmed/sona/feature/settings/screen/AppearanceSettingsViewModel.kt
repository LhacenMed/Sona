package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored theme settings the [AppearanceScreen] rows are connected to. */
@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val themeSettings: ThemeSettings,
) : ViewModel() {

    val roundMode: StateFlow<Boolean> = themeSettings.roundMode.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), themeSettings.roundMode.value)

    fun setRoundMode(enabled: Boolean) {
        viewModelScope.launch { themeSettings.setRoundMode(enabled) }
    }
}
