package com.lhacenmed.sona.feature.settings

import androidx.lifecycle.ViewModel
import com.lhacenmed.sona.core.navigation.DebugTools
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Optional
import javax.inject.Inject

/** What the settings home offers beyond its categories: the debug tools, in a build that has them. */
@HiltViewModel
class SettingsHomeViewModel @Inject constructor(debugTools: Optional<DebugTools>) : ViewModel() {
    val debugTools: DebugTools? = debugTools.orElse(null)
}
