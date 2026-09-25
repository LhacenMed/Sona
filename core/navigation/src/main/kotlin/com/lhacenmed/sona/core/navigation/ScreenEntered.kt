package com.lhacenmed.sona.core.navigation

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the screen has finished coming in - its activity's enter animation over - so motion meant to
 * be watched, like scrolling to a setting, starts once it can be seen rather than behind the transition.
 *
 * [HostActivity] provides its own; anywhere else a screen is simply taken as in.
 */
val LocalScreenEntered = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(true) }
