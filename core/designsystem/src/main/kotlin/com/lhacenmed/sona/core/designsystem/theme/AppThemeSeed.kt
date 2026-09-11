package com.lhacenmed.sona.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.StateFlow

/**
 * The colour the whole app is themed from, for whoever is willing to work it out.
 *
 * The seed is derived from the playing track's artwork, which only the app layer can see - this
 * module knows how to turn a colour into a scheme, not where the colour comes from. Declaring the
 * need here rather than reaching for the answer is what lets *every* activity theme itself: the
 * shared host in `:core:navigation` cannot depend on `:app`, but it can depend on this.
 *
 * Implementations are process-wide singletons. A theme that were per-activity would recompute the
 * palette on each navigation, and would flash the default colour on the way.
 */
interface AppThemeSeed {
    val color: StateFlow<Color>
}
