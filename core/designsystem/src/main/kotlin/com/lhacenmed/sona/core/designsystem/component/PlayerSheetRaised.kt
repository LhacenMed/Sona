package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the player is raised above its mini player - being dragged up, expanding, or open - which is
 * when what floats over a screen's bottom steps aside rather than being covered: Auxio hides its
 * floating buttons the moment the playback sheet starts to rise.
 *
 * Provided by the player that covers the screen, alongside [LocalBottomContentPadding].
 */
val LocalPlayerSheetRaised = compositionLocalOf { false }
