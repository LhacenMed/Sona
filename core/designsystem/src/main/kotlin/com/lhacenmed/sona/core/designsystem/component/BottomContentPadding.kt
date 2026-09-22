package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp

/**
 * The space a screen's scrolling content ends with, so its last row can scroll clear of what is laid
 * over the bottom of every screen: the navigation bar, and the mini player above it.
 *
 * Provided by the player that covers the screen, which is the one thing that knows how much it
 * covers, and read by each scrolling container - a list as its bottom content padding, a scrolling
 * column as padding inside its scroll. Content still draws behind the player and the bar; only its
 * end is held clear of them.
 */
val LocalBottomContentPadding = compositionLocalOf<Dp> {
    error("No bottom content padding provided - is the screen inside the player overlay?")
}
