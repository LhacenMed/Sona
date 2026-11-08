package com.lhacenmed.sona.feature.player.background

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.lhacenmed.sona.core.designsystem.player.PlayerCheckedColors

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
    /** A control that is on, and play while paused: the second accent, beside [button]'s. */
    val checked: PlayerCheckedColors,
)

/**
 * The theme's own colours, over every background - each is drawn in the theme's tones, see [PlayerBackground] -
 * and the play button and seek bar in the theme's secondary colour.
 */
@Composable
internal fun playerColors(): PlayerColors {
    val colorScheme = MaterialTheme.colorScheme
    return PlayerColors(
        content = colorScheme.onBackground,
        button = colorScheme.secondary,
        onButton = colorScheme.onSecondary,
        checked = PlayerCheckedColors(container = colorScheme.secondaryContainer, content = colorScheme.onSecondaryContainer),
    )
}
