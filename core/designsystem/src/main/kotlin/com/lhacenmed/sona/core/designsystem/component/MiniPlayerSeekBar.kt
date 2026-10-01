package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the screens beneath the mini player ask of it - see [ShowMiniPlayerSeekBar]. The player provides one for the
 * screen it lays itself over, and reads it where it draws the mini player.
 */
@Stable
class MiniPlayerSeekBarRequests {
    private val actions = mutableStateListOf<State<TopBarAction>>()

    /** What the screen that asked last puts beside play and pause, as it stands now - null while none asks. */
    val action: TopBarAction? get() = actions.lastOrNull()?.value

    internal fun request(action: State<TopBarAction>) {
        actions += action
    }

    internal fun release(action: State<TopBarAction>) {
        actions -= action
    }
}

val LocalMiniPlayerSeekBarRequests = staticCompositionLocalOf<MiniPlayerSeekBarRequests?> { null }

/**
 * Has the mini player show its seek bar - where the track is, and a slider to move through it - in place of the
 * track's names, with play and pause and the screen's own [action] in place of the transport, for as long as this
 * is composed: for a screen that works against the track as it plays. [action] is read as it stands, so it can
 * show what it toggles.
 */
@Composable
fun ShowMiniPlayerSeekBar(action: TopBarAction) {
    val requests = LocalMiniPlayerSeekBarRequests.current ?: return
    val latestAction = rememberUpdatedState(action)
    DisposableEffect(requests, latestAction) {
        requests.request(latestAction)
        onDispose { requests.release(latestAction) }
    }
}
