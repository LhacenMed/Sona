package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

private typealias OverlayContent = @Composable BoxScope.() -> Unit

private val LocalWindowOverlays = staticCompositionLocalOf<SnapshotStateList<State<OverlayContent>>> {
    error("No WindowOverlayHost provided")
}

/**
 * The top of an activity's window: whatever a screen lays there with [WindowOverlay] is drawn over
 * [content] - the player laid over every screen included - so it can cover the whole app.
 *
 * Every activity's root wraps its content in one, inside its theme and navigator, so what is laid here
 * looks and navigates as the screen that laid it does.
 */
@Composable
fun WindowOverlayHost(content: @Composable () -> Unit) {
    val overlays = remember { mutableStateListOf<State<OverlayContent>>() }
    Box(modifier = Modifier.fillMaxSize(), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalWindowOverlays provides overlays, content = content)
        Box(modifier = Modifier.fillMaxSize()) {
            overlays.forEach { overlay -> key(overlay) { overlay.value(this) } }
        }
    }
}

/**
 * Lays [content] over the whole window, above everything the window shows, for as long as this is
 * composed - laid out against the window rather than where it is called from.
 *
 * It is composed at the host, apart from the caller: it sees the host's composition locals, not the
 * caller's, and [content] is not run again just because the caller recomposed - Compose keeps one object
 * for a composable lambda and skips running it again when nothing it is given changed. So [content] reads
 * whatever changes from state, held by something it captures, rather than capturing the values
 * themselves, which would stay as they were when it was first drawn.
 */
@Composable
fun WindowOverlay(content: @Composable BoxScope.() -> Unit) {
    val overlays = LocalWindowOverlays.current
    val latestContent = rememberUpdatedState(content)
    DisposableEffect(overlays, latestContent) {
        overlays += latestContent
        onDispose { overlays -= latestContent }
    }
}

/**
 * Keeps every touch from the window for as long as this is composed - for motion the app is running on
 * the user's behalf, which a touch would otherwise interrupt. Back still works.
 */
@Composable
fun WindowTouchBlocker() {
    WindowOverlay {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
        )
    }
}
