package com.lhacenmed.sona.feature.settings.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.core.datastore.UpdateSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.feature.update.InstalledBuild
import com.lhacenmed.sona.feature.update.UpdateChecker
import com.lhacenmed.sona.feature.update.UpdateMonitor
import com.lhacenmed.sona.feature.update.UpdateRegistry
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.installedBuild
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a check the user asked for stands - what the Updates screen shows of it. */
sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Available(val release: Release) : UpdateCheck
    data class Failed(val message: String?) : UpdateCheck
}

/**
 * The Updates screen's state - ArchiveTune's `UpdateScreen`: the newest release on the chosen channel, and a
 * check the user asks for outright.
 *
 * The newest release is [UpdateMonitor]'s, which follows the channel - showing what was kept the moment it
 * changes, then GitHub's answer - for as long as the app is open; this screen only reads it.
 */
@HiltViewModel
class UpdatesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateSettings: UpdateSettings,
    private val updateMonitor: UpdateMonitor,
) : ViewModel() {

    val channel: StateFlow<UpdateChannel> = updateSettings.channel.stateIn(viewModelScope)
    val notifications: StateFlow<Boolean> = updateSettings.notifications.stateIn(viewModelScope)
    val autoPrompt: StateFlow<Boolean> = updateSettings.autoPrompt.stateIn(viewModelScope)

    /** The newest release on the channel, or null until it is known. */
    val latest: StateFlow<Release?> = UpdateRegistry.latest

    private val _check = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    val check: StateFlow<UpdateCheck> = _check.asStateFlow()

    /** What is installed - which build of which version, and whether it can be updated from here. */
    val installedBuild: InstalledBuild = context.installedBuild()

    fun isUpdate(release: Release): Boolean = UpdateChecker.isUpdate(context, release)

    /** Asks GitHub now, whatever was kept - the Check for update button. */
    fun checkForUpdate() {
        if (_check.value == UpdateCheck.Checking) return
        _check.value = UpdateCheck.Checking
        viewModelScope.launch {
            _check.value = updateMonitor.checkNow().fold(
                onSuccess = { latest ->
                    if (isUpdate(latest)) {
                        // Offered here, in this screen's sheet, so the automatic one is not raised over it.
                        UpdateRegistry.dismissPrompt(latest.versionName)
                        UpdateCheck.Available(latest)
                    } else {
                        UpdateCheck.UpToDate
                    }
                },
                onFailure = { UpdateCheck.Failed(it.message) },
            )
        }
    }

    /**
     * Offers [release] in the update sheet - the card's Update button, pressed. The automatic prompt is put
     * away with it: this is the same offer, asked for.
     */
    fun reviewUpdate(release: Release) {
        UpdateRegistry.dismissPrompt(release.versionName)
        _check.value = UpdateCheck.Available(release)
    }

    fun dismissCheck() {
        _check.value = UpdateCheck.Idle
    }

    fun setChannel(channel: UpdateChannel) {
        viewModelScope.launch { updateSettings.setChannel(channel) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { updateSettings.setNotifications(enabled) }
    }

    fun setAutoPrompt(enabled: Boolean) {
        viewModelScope.launch { updateSettings.setAutoPrompt(enabled) }
    }
}
