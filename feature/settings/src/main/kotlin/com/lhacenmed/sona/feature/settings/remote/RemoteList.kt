package com.lhacenmed.sona.feature.settings.remote

import com.lhacenmed.sona.core.common.network.NetworkMonitor
import com.lhacenmed.sona.feature.settings.component.SettingsLoad
import com.lhacenmed.sona.feature.settings.component.settingsLoadOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A list a settings screen reads from GitHub - the contributors, the releases, the commits: the one kept from
 * last time at once, then asked for again each time the device is online while the screen is open - and
 * outright on [retry]. A failure never takes away a list already shown; offline with nothing kept, it says
 * so rather than waiting.
 *
 * [cached] is what was kept, null for nothing; [fetch] asks for the list, [fetch]`(true)` without waiting on
 * how recently it was last asked.
 */
class RemoteList<T>(
    private val scope: CoroutineScope,
    networkMonitor: NetworkMonitor,
    cached: () -> List<T>?,
    private val fetch: suspend (force: Boolean) -> Result<List<T>>,
) {
    private val _load = MutableStateFlow(cached()?.let(::settingsLoadOf) ?: SettingsLoad.Loading)
    val load: StateFlow<SettingsLoad<T>> = _load.asStateFlow()

    init {
        scope.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                when {
                    isOnline -> refresh(force = false)
                    _load.value == SettingsLoad.Loading -> _load.value = SettingsLoad.Failed
                }
            }
        }
    }

    /** Asks GitHub now - the Retry row. */
    fun retry() {
        if (_load.value !is SettingsLoad.Loaded) _load.value = SettingsLoad.Loading
        scope.launch { refresh(force = true) }
    }

    private suspend fun refresh(force: Boolean) {
        fetch(force)
            .onSuccess { _load.value = settingsLoadOf(it) }
            .onFailure { if (_load.value !is SettingsLoad.Loaded) _load.value = SettingsLoad.Failed }
    }
}
