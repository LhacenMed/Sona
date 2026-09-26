package com.lhacenmed.sona.feature.settings.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.core.datastore.UpdateSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.feature.update.UpdateChecker
import com.lhacenmed.sona.feature.update.github.Commit
import com.lhacenmed.sona.feature.update.github.Commits
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.github.Releases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Where a check the user asked for stands - what the Updates screen shows over itself. */
sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data class UpToDate(val versionName: String) : UpdateCheck
    data class Available(val release: Release) : UpdateCheck
    data class Failed(val message: String?) : UpdateCheck
}

/**
 * The Updates screen's state - ArchiveTune's `UpdateScreen`: the newest release on the chosen channel, the
 * development branch's recent commits, and a check the user asks for outright.
 */
@HiltViewModel
class UpdatesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateSettings: UpdateSettings,
) : ViewModel() {

    val channel: StateFlow<UpdateChannel> = updateSettings.channel.stateIn(viewModelScope)
    val notifications: StateFlow<Boolean> = updateSettings.notifications.stateIn(viewModelScope)

    private val _latest = MutableStateFlow<Release?>(null)

    /** The newest release on the channel, or null until it is known. */
    val latest: StateFlow<Release?> = _latest.asStateFlow()

    private val _commits = MutableStateFlow<List<Commit>?>(null)

    /** The development branch's recent commits, or null while they load. */
    val commits: StateFlow<List<Commit>?> = _commits.asStateFlow()

    private val _check = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    val check: StateFlow<UpdateCheck> = _check.asStateFlow()

    init {
        // Each channel opens on its kept releases at once, then on GitHub's once they are asked for.
        viewModelScope.launch {
            channel.collectLatest { channel ->
                _latest.value = Releases.cached(context, channel).firstOrNull()
                UpdateChecker.check(context, channel).onSuccess { _latest.value = it }
            }
        }
        viewModelScope.launch { _commits.value = Commits.recent().getOrDefault(emptyList()) }
    }

    fun isNewer(release: Release): Boolean = UpdateChecker.isNewer(context, release)

    /** Asks GitHub now, whatever was kept - the Check for update button. */
    fun checkForUpdate() {
        if (_check.value == UpdateCheck.Checking) return
        _check.value = UpdateCheck.Checking
        viewModelScope.launch {
            _check.value = UpdateChecker.check(context, channel.value, forceRefresh = true).fold(
                onSuccess = { latest ->
                    _latest.value = latest
                    if (isNewer(latest)) UpdateCheck.Available(latest) else UpdateCheck.UpToDate(latest.versionName)
                },
                onFailure = { UpdateCheck.Failed(it.message) },
            )
        }
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
}
