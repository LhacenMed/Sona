package com.lhacenmed.sona.core.navigation

import androidx.compose.runtime.Composable

/**
 * The player every activity lays over its content.
 *
 * Declared here because [HostActivity] draws it but cannot see the player, which lives in a feature
 * module. The app binds the one implementation, so every activity draws the same player.
 *
 * It wraps the activity's [content] rather than sitting beside it, so it can tell the screen how much
 * of its bottom it covers - which every scrolling list keeps clear at its end.
 */
interface PlayerOverlay {
    @Composable
    fun Content(content: @Composable () -> Unit)
}
