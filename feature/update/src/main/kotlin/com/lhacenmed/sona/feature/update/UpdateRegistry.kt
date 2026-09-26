package com.lhacenmed.sona.feature.update

import com.lhacenmed.sona.feature.update.github.Release
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide source of truth for the app-update flow. Holds the [available] update (set by
 * [UpdateChecker]) and the live [state] of its download (written by [UpdateService], observed by the UI).
 * Living outside any Activity means a config change - or dismissing a dialog - never loses an in-flight
 * APK download: re-opening simply re-reads the same flow.
 */
object UpdateRegistry {

    private val _available = MutableStateFlow<Release?>(null)
    val available: StateFlow<Release?> = _available.asStateFlow()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Records the newer version there is, or that there is none; the UI prompts off this. */
    fun setAvailable(update: Release?) { _available.value = update }

    fun update(state: UpdateState) { _state.value = state }

    fun stateOf(): UpdateState = _state.value

    /** True while the APK is connecting or transferring. */
    val isActive: Boolean
        get() = when (_state.value) {
            UpdateState.Connecting, is UpdateState.Downloading -> true
            else -> false
        }

    /** True while the available update's APK is downloading, or downloaded and waiting to install. */
    internal val holdsDownload: Boolean
        get() = isActive || _state.value is UpdateState.Downloaded
}
