package com.lhacenmed.sona.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.lhacenmed.sona.core.designsystem.theme.palette.ThemeSeedPalette
import com.lhacenmed.sona.core.model.AppFont
import com.lhacenmed.sona.core.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/** Everything [SonaTheme] draws the app with that the user chooses - resolved down to what is drawn. */
@Immutable
data class ThemeConfig(
    val mode: ThemeMode,
    /** Black surfaces while the theme is dark. */
    val pureBlack: Boolean,
    val colors: ThemeColors,
    val font: AppFont,
    /** The picked font file while [font] is [AppFont.CUSTOM]; the default font stands in until it loads. */
    val customFontUri: String?,
)

/** Where the theme's colours come from, once every colour setting and the playing cover are accounted for. */
@Immutable
sealed interface ThemeColors {
    /** Material You: the system's colours from the wallpaper. Android 12 and later only. */
    data object Wallpaper : ThemeColors

    /** A scheme grown from one colour - the playing cover's. */
    data class Seed(val color: Color) : ThemeColors

    /** A scheme grown from a palette's four seeds, one per tonal role. */
    data class Palette(val seeds: ThemeSeedPalette) : ThemeColors
}

/**
 * The app's current [ThemeConfig], for whichever activity themes itself.
 *
 * The colours depend on the playing cover, which only the app layer can see - this module knows how to
 * draw a config, not where its parts come from. Declaring the need here is what lets *every* activity
 * theme itself: the shared host in `:core:navigation` cannot depend on `:app`, but it can depend on this.
 *
 * Implementations are process-wide singletons, so navigating never works the theme out again, nor
 * flashes a default on the way.
 */
interface AppTheme {
    val config: StateFlow<ThemeConfig>
}
