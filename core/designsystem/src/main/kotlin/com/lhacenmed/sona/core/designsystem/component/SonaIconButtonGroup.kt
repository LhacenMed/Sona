package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.R

/**
 * A row of [SonaIconButton]s that answer each other's presses.
 *
 * Holding one button widens it and compresses its neighbours to pay for the width it took, so the
 * row's own width never changes and nothing laid out around it moves. This is the half of the press
 * behaviour a button cannot do by itself, which is why Material only offers it inside a group: the
 * space a pressed button grows into has to come from somewhere, and only its neighbours know how
 * much they can spare.
 *
 * Buttons sit flush against one another. An icon button already pads itself out to a 48dp touch
 * target, so spacing on top of that would read as a gap between buttons rather than as one row of
 * them.
 */
@Composable
fun SonaIconButtonGroup(
    modifier: Modifier = Modifier,
    content: ButtonGroupScope.() -> Unit,
) {
    ButtonGroup(
        // Reached only if the row is ever too narrow to hold its buttons, which the two or three in
        // a top bar never are. Wired up regardless so that a row which does run out of room folds
        // into a menu rather than silently dropping the buttons that did not fit.
        overflowIndicator = { menuState ->
            SonaIconButton(
                onClick = menuState::show,
                icon = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.top_bar_more_actions),
            )
        },
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
    )
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
