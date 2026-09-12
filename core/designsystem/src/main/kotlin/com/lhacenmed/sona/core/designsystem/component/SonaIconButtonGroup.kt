package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.R

/**
 * The gap between two grouped buttons.
 *
 * An ungrouped icon button separates itself from its neighbour with the padding left over between
 * its 40dp container and the edge of its 48dp touch target. A grouped one has no such margin to
 * give - see [SonaIconButtonGroup] - so the row has to space the buttons itself to arrive at the
 * same 8dp between them.
 */
private val GroupedButtonSpacing = 8.dp

/**
 * A row of [SonaIconButton]s that answer each other's presses.
 *
 * Holding one button widens it and compresses its neighbours to pay for the width it took, so the
 * row's own width never changes and nothing laid out around it moves. This is the half of the press
 * behaviour a button cannot do by itself, which is why Material only offers it inside a group: the
 * space a pressed button grows into has to come from somewhere, and only its neighbours know how
 * much they can spare.
 *
 * The group is what decides how wide each button is: it measures them once, then re-measures every
 * one at a width it fixes itself, so it can hand width between them as the press moves along the
 * row. An icon button paints its container across the whole width it is handed, which is what makes
 * the press visible - but it also means the container is only ever as round as that width is equal
 * to its 40dp height.
 *
 * Left alone the width it gets is 48dp, not 40dp, because an icon button asks for the size of its
 * touch target rather than of its container. Inside a group that inflation is doubly wrong: it
 * stretches the container into an oval, and it spends on the container the very padding that
 * normally separates one button from the next. Turning it off here restores both at once - the
 * buttons measure at the 40dp they actually draw, and [GroupedButtonSpacing] puts back the gap they
 * can no longer hold themselves.
 *
 * The cost is that a grouped button's touch target is its 40dp container rather than 48dp. That is
 * the one thing a group cannot give back: the width it hands out is the width that gets painted, so
 * a target larger than the container would be an oval again.
 */
@Composable
fun SonaIconButtonGroup(
    modifier: Modifier = Modifier,
    content: ButtonGroupScope.() -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        ButtonGroup(
            // Reached only if the row is ever too narrow to hold its buttons, which the two or three
            // in a top bar never are. Wired up regardless so that a row which does run out of room
            // folds into a menu rather than silently dropping the buttons that did not fit.
            overflowIndicator = { menuState ->
                SonaIconButton(
                    onClick = menuState::show,
                    icon = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.top_bar_more_actions),
                )
            },
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(GroupedButtonSpacing),
            content = content,
        )
    }
}

/**
 * One button in a [SonaIconButtonGroup].
 *
 * Declaring a button this way rather than placing a [SonaIconButton] into the group directly is what
 * joins it to its neighbours: the press has to be visible both to the button, which morphs, and to
 * the group, which widens it, so the two are given one interaction source to share. [label]
 * describes the button and is also what it says for itself if it ever has to fall back into the
 * overflow menu.
 */
fun ButtonGroupScope.iconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) = customItem(
    buttonGroupContent = {
        val interactionSource = remember { MutableInteractionSource() }
        SonaIconButton(
            onClick = onClick,
            icon = icon,
            contentDescription = label,
            // No size of its own: the width is the group's to animate, and pinning it here would
            // leave the press with nothing to grow into.
            modifier = Modifier.animateWidth(interactionSource),
            interactionSource = interactionSource,
        )
    },
    menuContent = { menuState ->
        DropdownMenuItem(
            text = { Text(label) },
            leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
            onClick = {
                menuState.dismiss()
                onClick()
            },
        )
    },
)
