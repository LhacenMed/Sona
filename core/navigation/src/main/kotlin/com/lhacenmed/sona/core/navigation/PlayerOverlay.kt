package com.lhacenmed.sona.core.navigation

import androidx.compose.runtime.Composable

/**
 * The player every activity lays over its content.
 *
 * Declared here because [HostActivity] draws it but cannot see the player, which lives in a feature
 * module. The app binds the one implementation, so every activity draws the same player.
 */
interface PlayerOverlay {
    @Composable
    fun Content()
}
