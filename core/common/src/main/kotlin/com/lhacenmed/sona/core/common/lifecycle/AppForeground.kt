package com.lhacenmed.sona.core.common.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether the app is in the foreground - true while any of its activities is started, whichever one -
 * starting with where it is now. The one place the process's visibility is read.
 */
fun isAppInForeground(): Flow<Boolean> =
    ProcessLifecycleOwner.get().lifecycle.currentStateFlow
        .map { it.isAtLeast(Lifecycle.State.STARTED) }
        .distinctUntilChanged()
