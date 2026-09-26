package com.lhacenmed.sona.feature.player.background

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.lhacenmed.sona.core.datastore.PlayerBackgroundStyle
import com.lhacenmed.sona.core.datastore.PlayerButtonsStyle

/**
 * What the expanded player draws its text and controls in - ArchiveTune's `TextBackgroundColor`,
 * `icBackgroundColor` and `textButtonColor`.
 */
@Immutable
internal data class PlayerColors(
    /** Text, icons, and the quieter buttons' tint. */
    val content: Color,
    /** What is drawn on [button]: the play button's icon. */
    val onContent: Color,
    /** The play button and the seek bar. */
    val button: Color,
)

/**
 * The theme's own colours over its own surface; white over any other background, which is drawn dark
 * enough to read it - and the play button and seek bar in the theme's secondary colour, if chosen.
 */
@Composable
internal fun playerColors(background: PlayerBackgroundStyle, buttonsStyle: PlayerButtonsStyle): PlayerColors {
    val colorScheme = MaterialTheme.colorScheme
    val followsTheme = background == PlayerBackgroundStyle.DEFAULT
    val content = if (followsTheme) colorScheme.onBackground else Color.White
    return PlayerColors(
        content = content,
        onContent = if (followsTheme) colorScheme.surface else Color.Black,
        button = when (buttonsStyle) {
            PlayerButtonsStyle.DEFAULT -> content
            PlayerButtonsStyle.SECONDARY -> colorScheme.secondary
        },
    )
}
