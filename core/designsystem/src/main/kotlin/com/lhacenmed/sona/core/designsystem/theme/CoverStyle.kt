package com.lhacenmed.sona.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * How every cover in the app is drawn: whether there are covers at all, how large they are decoded,
 * and how they are cut.
 *
 * The cover settings are stored in `:core:datastore`, which this module cannot see, so the app turns
 * them into one of these - the same split as [AppThemeSeed].
 */
@Immutable
data class CoverStyle(
    /** False when covers are turned off, which leaves every cover showing its placeholder. */
    val showsCovers: Boolean,
    /** The largest either side of a cover is decoded at, in pixels, or null for no limit. */
    val maxResolutionPx: Int?,
    /** Crop covers to a square instead of fitting the whole image inside one. */
    val isForcedSquare: Boolean,
    /**
     * Round mode: round the corners of covers - and, through [SonaTheme], of every shape in the app;
     * square when false. See [LocalIsRounded].
     */
    val isRounded: Boolean,
)

/** The [CoverStyle] every cover below [SonaTheme] is drawn with. */
val LocalCoverStyle = compositionLocalOf<CoverStyle> {
    error("No CoverStyle provided: covers must be drawn inside SonaTheme")
}

/** The app's current [CoverStyle], for whichever activity themes itself. */
interface AppCoverStyle {
    val style: StateFlow<CoverStyle>
}
