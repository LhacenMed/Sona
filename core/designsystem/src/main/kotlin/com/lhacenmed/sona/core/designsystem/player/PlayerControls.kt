@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.core.designsystem.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.designsystem.component.SonaIconButtonGroup
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.iconTogglePressShapes
import com.lhacenmed.sona.core.designsystem.theme.togglePressShapes
import com.lhacenmed.sona.core.model.RepeatMode

/**
 * The measures every player's controls are built from - the music player's, its lyrics sheet's and the video player's -
 * so the players read as one design, and retuning one value here retunes them all.
 *
 * A row of controls stands its buttons' own bounds on the player's keyline - what a press, or a control that is on,
 * shows - rather than their glyphs.
 */
object PlayerControls {
    /** A control of a player's bars: its touch target, and the glyph within it. */
    val BarButtonSize = 44.dp
    val BarIconSize = 22.dp

    /** The transport: previous and next either side of play, [TransportGap] apart where the room allows. */
    val SkipButtonSize = 56.dp
    val SkipIconSize = 28.dp
    val PlayButtonSize = 72.dp
    val PlayIconSize = 32.dp
    val TransportGap = 32.dp

    /** How quiet a player's secondary text is - an artist, a header's second line, the time at the seek bar's end. */
    const val SecondaryAlpha = 0.72f

    /**
     * The transport's gap across [width]: its controls - with [hasCorners], shuffle and repeat at its ends too - as far
     * apart as the room allows, up to [TransportGap].
     */
    fun transportGap(width: Dp, hasCorners: Boolean): Dp {
        val corners = if (hasCorners) BarButtonSize * 2 else 0.dp
        val gaps = if (hasCorners) 4 else 2
        return ((width - corners - SkipButtonSize * 2 - PlayButtonSize) / gaps).coerceIn(0.dp, TransportGap)
    }
}

/** The repeat control's glyph for [this] mode - every player's. */
fun RepeatMode.transportIcon(): ImageVector =
    when (this) {
        RepeatMode.OFF, RepeatMode.ALL -> PlayerIcons.Repeat
        RepeatMode.ONE -> PlayerIcons.RepeatOne
        RepeatMode.STOP_AFTER_CURRENT -> PlayerIcons.RepeatStop
    }

/**
 * What a player's control that is on is drawn in - shuffle, repeat, the lyrics open, play while paused: its container,
 * squared - see [iconTogglePressShapes] - and its glyph. Each player provides its own once, through
 * [LocalPlayerCheckedColors], for every control within it.
 */
@Immutable
class PlayerCheckedColors(val container: Color, val content: Color)

val LocalPlayerCheckedColors = staticCompositionLocalOf<PlayerCheckedColors> { error("No player provides its checked colours") }

/**
 * A control standing alone in a player's bar - the way back, the lock: Material's icon button, pressing as every press in
 * the app does, its glyph [PlayerControls.BarIconSize] within a [PlayerControls.BarButtonSize] target, in [color]. With
 * [checked], it is a toggle - drawn in [LocalPlayerCheckedColors], squared, while on.
 */
@Composable
fun PlayerBarButton(
    icon: ImageVector,
    contentDescription: String?,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checked: Boolean? = null,
) {
    PlayerIconButton(
        checked = checked,
        onClick = onClick,
        color = color,
        enabled = enabled,
        modifier = modifier.size(PlayerControls.BarButtonSize),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(PlayerControls.BarIconSize))
    }
}

/**
 * Controls standing together in a player's bar - a held one widening at its neighbours' expense, Material 3 Expressive's
 * button group, see [SonaIconButtonGroup]. Each is its own touch target, [spacing] apart, so one that is on stands clear
 * of the next.
 */
@Composable
fun PlayerButtonGroup(modifier: Modifier = Modifier, spacing: Dp = SonaComponentStyle.ItemSpacing, content: ButtonGroupScope.() -> Unit) {
    SonaIconButtonGroup(modifier = modifier, spacing = spacing, content = content)
}

/**
 * A control in a [PlayerButtonGroup]: [icon] in [color], [checked] as [PlayerBarButton] is. [onClick] is null for a
 * control that cannot act right now. [modifier] is applied beneath the group's width animation.
 */
fun ButtonGroupScope.playerButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: (() -> Unit)?,
    checked: Boolean? = null,
    size: Dp = PlayerControls.BarButtonSize,
    iconSize: Dp = PlayerControls.BarIconSize,
    containerColor: Color = Color.Transparent,
    modifier: Modifier = Modifier,
) = customItem(
    buttonGroupContent = {
        val interactionSource = remember { MutableInteractionSource() }
        PlayerIconButton(
            checked = checked,
            onClick = { onClick?.invoke() },
            color = color,
            enabled = onClick != null,
            containerColor = containerColor,
            interactionSource = interactionSource,
            // The group's width comes first, and this gives way to it: all a press has to grow into.
            modifier = Modifier.animateWidth(interactionSource).then(modifier).size(size),
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(iconSize))
        }
    },
    menuContent = { menuState -> PlayerMenuItem(label, onClick, menuState::dismiss) },
)

/**
 * A control in a [PlayerButtonGroup] saying [text] for an icon - the speed, a sleep timer's time left - as wide as it
 * takes, and never narrower than any other control; [checked] as [PlayerBarButton] is.
 */
fun ButtonGroupScope.playerTextButton(text: String, label: String, color: Color, checked: Boolean = false, onClick: () -> Unit) =
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            val checkedColors = LocalPlayerCheckedColors.current
            ToggleButton(
                checked = checked,
                onCheckedChange = { onClick() },
                shapes = togglePressShapes(),
                colors = ToggleButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = color,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = color.copy(alpha = DisabledAlpha),
                    checkedContainerColor = checkedColors.container,
                    checkedContentColor = checkedColors.content,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
                interactionSource = interactionSource,
                modifier = Modifier
                    .animateWidth(interactionSource)
                    .heightIn(min = PlayerControls.BarButtonSize, max = PlayerControls.BarButtonSize)
                    .widthIn(min = PlayerControls.BarButtonSize)
                    .semantics { contentDescription = label },
            ) {
                Text(text = text, style = playerControlLabelStyle(), maxLines = 1)
            }
        },
        menuContent = { menuState -> PlayerMenuItem(label, onClick, menuState::dismiss) },
    )

/** A player's icon control: a plain icon button, or - with [checked] - a toggle, squared in the player's checked colours while on. */
@Composable
private fun PlayerIconButton(
    checked: Boolean?,
    onClick: () -> Unit,
    color: Color,
    enabled: Boolean,
    modifier: Modifier,
    containerColor: Color = Color.Transparent,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (checked == null) {
        IconButton(
            onClick = onClick,
            shapes = iconButtonPressShapes(),
            enabled = enabled,
            colors = IconButtonColors(containerColor, color, containerColor, color.copy(alpha = DisabledAlpha)),
            interactionSource = interactionSource,
            modifier = modifier,
            content = content,
        )
    } else {
        val checkedColors = LocalPlayerCheckedColors.current
        IconToggleButton(
            checked = checked,
            onCheckedChange = { onClick() },
            shapes = iconTogglePressShapes(),
            enabled = enabled,
            colors = IconToggleButtonColors(
                containerColor = containerColor,
                contentColor = color,
                disabledContainerColor = containerColor,
                disabledContentColor = color.copy(alpha = DisabledAlpha),
                checkedContainerColor = checkedColors.container,
                checkedContentColor = checkedColors.content,
            ),
            interactionSource = interactionSource,
            modifier = modifier,
            content = content,
        )
    }
}

/** A control as the group's overflow menu lists it, should the bar ever run out of room for it. */
@Composable
internal fun PlayerMenuItem(label: String, onClick: (() -> Unit)?, dismiss: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            dismiss()
            onClick?.invoke()
        },
        enabled = onClick != null,
    )
}

/** What a control that cannot act right now keeps of its colour. */
internal const val DisabledAlpha = 0.38f

@Composable
@ReadOnlyComposable
private fun playerControlLabelStyle() =
    MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
