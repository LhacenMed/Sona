package com.lhacenmed.sona.feature.player.background

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.lhacenmed.sona.core.datastore.PlayerBackgroundStyle

/**
 * What the expanded player draws its text and controls in - ArchiveTune's `TextBackgroundColor`,
 * `icBackgroundColor` and `textButtonColor`.
 */
@Immutable
internal data class PlayerColors(
    /** Text, icons, and the quieter buttons' tint. */
    val content: Color,
    /** The play button and the seek bar. */
    val button: Color,
    /** What is drawn on [button]: the play button's icon. */
    val onButton: Color,
)

/**
 * The theme's own colours over its own surface; white over any other background, which is drawn dark
 * enough to read it - and the play button and seek bar in the theme's secondary colour, over either.
 */
@Composable
internal fun playerColors(background: PlayerBackgroundStyle): PlayerColors {
    val colorScheme = MaterialTheme.colorScheme
    return PlayerColors(
        content = if (background == PlayerBackgroundStyle.FOLLOW_THEME) colorScheme.onBackground else Color.White,
        button = colorScheme.secondary,
        onButton = colorScheme.onSecondary,
    )
}
