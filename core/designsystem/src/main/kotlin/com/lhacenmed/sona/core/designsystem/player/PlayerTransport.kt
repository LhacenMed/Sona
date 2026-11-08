@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.core.designsystem.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes

/** What a [PlayerTransport] is drawn in: its glyphs, and what play and the skips stand on. */
@Immutable
class PlayerTransportColors(
    val content: Color,
    val playContainer: Color,
    val playContent: Color,
    val skipContainer: Color = Color.Transparent,
)

/**
 * Previous, play or pause, and next - what a thumb reaches for most, in every player - as one Material 3 Expressive button
 * group: a held one widens at its neighbours' expense. [playIcon] and [playLabel] say what play does now. Play stands on
 * [PlayerTransportColors.playContainer] while playing, and - [isPaused] - in the player's checked colours, see
 * [LocalPlayerCheckedColors]. While [isLoading], a loader stands in play's glyph, the button under it still
 * answering. [onPrevious] or [onNext] is null where there is no skipping that way, and none of the three acts while not
 * [enabled]; [controlsModifier] is applied to each of them.
 *
 * [leading] and [trailing] add a player's own controls at either end - the music player's shuffle and repeat - to the
 * same group, so a press on any of them is answered by all.
 */
@Composable
fun PlayerTransport(
    playIcon: ImageVector,
    playLabel: String,
    isPaused: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    colors: PlayerTransportColors,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    spacing: Dp = PlayerControls.TransportGap,
    controlsModifier: Modifier = Modifier,
    leading: ButtonGroupScope.() -> Unit = {},
    trailing: ButtonGroupScope.() -> Unit = {},
) {
    val previousLabel = stringResource(R.string.player_control_previous)
    val nextLabel = stringResource(R.string.player_control_next)
    PlayerButtonGroup(modifier = modifier, spacing = spacing) {
        leading()
        playerButton(
            icon = PlayerIcons.SkipPrevious,
            label = previousLabel,
            color = colors.content,
            onClick = onPrevious?.takeIf { enabled },
            size = PlayerControls.SkipButtonSize,
            iconSize = PlayerControls.SkipIconSize,
            containerColor = colors.skipContainer,
            modifier = controlsModifier,
        )
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                PlayButton(
                    icon = playIcon,
                    label = playLabel,
                    isPaused = isPaused,
                    isLoading = isLoading,
                    onClick = onPlayPause.takeIf { enabled },
                    colors = colors,
                    interactionSource = interactionSource,
                    modifier = Modifier.animateWidth(interactionSource).then(controlsModifier).size(PlayerControls.PlayButtonSize),
                )
            },
            menuContent = { menuState -> PlayerMenuItem(playLabel, onPlayPause.takeIf { enabled }, menuState::dismiss) },
        )
        playerButton(
            icon = PlayerIcons.SkipNext,
            label = nextLabel,
            color = colors.content,
            onClick = onNext?.takeIf { enabled },
            size = PlayerControls.SkipButtonSize,
            iconSize = PlayerControls.SkipIconSize,
            containerColor = colors.skipContainer,
            modifier = controlsModifier,
        )
        trailing()
    }
}

/** Play or pause: on its own colour, turning to the checked colours while [isPaused] - see [PlayerTransport]. */
@Composable
private fun PlayButton(
    icon: ImageVector,
    label: String,
    isPaused: Boolean,
    isLoading: Boolean,
    onClick: (() -> Unit)?,
    colors: PlayerTransportColors,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
) {
    val checkedColors = LocalPlayerCheckedColors.current
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(if (isPaused) checkedColors.container else colors.playContainer, motion.defaultEffectsSpec(), label = "playContainer")
    val content by animateColorAsState(if (isPaused) checkedColors.content else colors.playContent, motion.defaultEffectsSpec(), label = "playContent")
    IconButton(
        onClick = { onClick?.invoke() },
        shapes = iconButtonPressShapes(),
        enabled = onClick != null,
        colors = IconButtonColors(container, content, container, content.copy(alpha = DisabledAlpha)),
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        if (isLoading) {
            CircularWavyProgressIndicator(modifier = Modifier.size(PlayerControls.PlayIconSize), color = content)
        } else {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(PlayerControls.PlayIconSize))
        }
    }
}
