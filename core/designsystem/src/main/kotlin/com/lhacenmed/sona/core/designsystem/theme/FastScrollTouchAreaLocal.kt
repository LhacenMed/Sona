package com.lhacenmed.sona.core.designsystem.theme

import androidx.compose.runtime.compositionLocalOf
import com.lhacenmed.sona.core.model.FastScrollTouchArea
import kotlinx.coroutines.flow.StateFlow

/** The [FastScrollTouchArea] every fast scroller below [SonaTheme] grabs its thumb with. */
val LocalFastScrollTouchArea = compositionLocalOf<FastScrollTouchArea> {
    error("No FastScrollTouchArea provided: fast scrollers must be drawn inside SonaTheme")
}

/**
 * The app's current [FastScrollTouchArea], for whichever activity themes itself.
 *
 * The setting is stored in `:core:datastore`, which this module cannot see, so the app hands it over -
 * the same split as [AppCoverStyle].
 */
interface AppFastScrollTouchArea {
    val touchArea: StateFlow<FastScrollTouchArea>
}
