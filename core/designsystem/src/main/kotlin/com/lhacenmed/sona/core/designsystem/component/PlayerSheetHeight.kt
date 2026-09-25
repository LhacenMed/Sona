package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How high the player laid over the screen reaches up from the window's bottom - what floats over the
 * screen's bottom stands on: nothing while no track is loaded, the mini player's height while it rests
 * collapsed, and more as the player rises.
 *
 * [value] follows the player itself - its slide in and out and every frame of a drag - so read where it
 * is laid out or drawn, it moves along with it without recomposing anything.
 */
@Stable
class PlayerSheetHeight(private val current: () -> Dp, private val collapsed: Dp) {
    val value: Dp
        get() = current()

    /**
     * Whether the player is raised above its mini player - being dragged up, expanding, or open - which
     * is when what floats over the screen steps aside: Auxio hides its floating buttons the moment the
     * playback sheet starts to rise.
     */
    val isRaised: Boolean
        get() = current() > collapsed
}

/**
 * Provided by the player that covers the screen, alongside [LocalBottomContentPadding]; a screen with no
 * player over it has nothing to stand clear of.
 */
val LocalPlayerSheetHeight = compositionLocalOf { PlayerSheetHeight(current = { 0.dp }, collapsed = 0.dp) }
