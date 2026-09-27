package com.lhacenmed.sona.core.navigation

import androidx.compose.runtime.Composable

/**
 * What the app raises over whatever screen is showing - an update found, say - drawn by every activity
 * over its content, so it reaches the user wherever they are.
 *
 * Declared here because [HostActivity] draws it but cannot see the features the prompts belong to. The app
 * binds the one implementation, so every activity raises the same prompts.
 */
interface AppPrompts {
    @Composable
    fun Content()
}
