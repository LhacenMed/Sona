package com.lhacenmed.sona.feature.update

import com.lhacenmed.sona.feature.update.github.Release
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide source of truth for the app-update flow: the [latest] release on the chosen channel, the
 * [available] update (both set by [UpdateChecker]), which update's prompt was [dismissedVersion] this session,
 * and the live [state] of its download (written by [UpdateService], observed by the UI). Living outside any
 * Activity means a config change, another activity or a closed dialog never loses any of it: re-opening
 * simply re-reads the same flows.
 */
object UpdateRegistry {

    private val _latest = MutableStateFlow<Release?>(null)

    /** The newest release on the chosen channel - an update or not - or null until one is known. */
    val latest: StateFlow<Release?> = _latest.asStateFlow()

    private val _available = MutableStateFlow<Release?>(null)
    val available: StateFlow<Release?> = _available.asStateFlow()

    private val _dismissedVersion = MutableStateFlow<String?>(null)

    /**
     * The version whose prompt was put away this session - so it is not raised again in this activity or
     * any other until a newer version turns up or the app starts again.
     */
    val dismissedVersion: StateFlow<String?> = _dismissedVersion.asStateFlow()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    internal fun setLatest(release: Release?) { _latest.value = release }

    /** Records the newer version there is, or that there is none; the UI prompts off this. */
    fun setAvailable(update: Release?) { _available.value = update }

    /** Puts away the prompt for [version] for the rest of the session. */
    fun dismissPrompt(version: String) { _dismissedVersion.value = version }

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
