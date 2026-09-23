package com.lhacenmed.sona.feature.settings.screen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.UpdateSettings
import com.lhacenmed.sona.feature.update.UpdateChecker
import com.lhacenmed.sona.feature.update.UpdateRegistry
import com.lhacenmed.sona.feature.update.UpdateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How the last manual check went, which the Check now row reports in place of its summary. */
enum class UpdateCheckStatus { IDLE, CHECKING, UP_TO_DATE }

/** The [UpdatesScreen]'s rows: whether a found update announces itself, and the way to ask. */
@HiltViewModel
class UpdatesSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateSettings: UpdateSettings,
) : ViewModel() {

    val autoPrompt: StateFlow<Boolean> = updateSettings.autoPrompt.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), updateSettings.autoPrompt.value)

    private val _checkStatus = MutableStateFlow(UpdateCheckStatus.IDLE)
    val checkStatus: StateFlow<UpdateCheckStatus> = _checkStatus.asStateFlow()

    fun setAutoPrompt(enabled: Boolean) {
        viewModelScope.launch { updateSettings.setAutoPrompt(enabled) }
    }

    /**
     * Asks about a newer build — unless one is already known, in which case the answer is in hand
     * and the dialog opens straight away. That covers the APK downloaded last session and still
     * waiting to install: it is "available" until the install goes through, so this reopens the
     * install prompt rather than re-fetching a manifest it already has.
     */
    fun checkForUpdate() {
        if (UpdateRegistry.available.value != null) return UpdateRegistry.requestPrompt()

        _checkStatus.value = UpdateCheckStatus.CHECKING
        viewModelScope.launch {
            val found = UpdateChecker.check(context)
            found?.let {
                UpdateStore.save(context, it)
                UpdateRegistry.setAvailable(it)
                UpdateRegistry.requestPrompt()
            }
            // A found update speaks for itself through the dialog; only its absence needs saying.
            _checkStatus.value = if (found == null) UpdateCheckStatus.UP_TO_DATE else UpdateCheckStatus.IDLE
        }
    }
}
