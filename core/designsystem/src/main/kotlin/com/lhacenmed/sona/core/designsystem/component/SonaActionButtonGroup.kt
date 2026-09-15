package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle

/**
 * The buttons at the foot of every dialog and sheet - Cancel, OK, Save and the like - answering each
 * other's presses the way [SonaIconButtonGroup]'s icons do.
 *
 * Holding one tightens its corners and widens it into the space its neighbour gives up, so the row's
 * width never changes and a dialog never reflows under the user's finger. A button with no neighbour
 * keeps its width and only its corners answer.
 *
 * In an `AlertDialog` the whole group goes in `confirmButton`, with `dismissButton` left out: Material
 * lays those two slots out as unrelated buttons, and a press can only be shared inside one group.
 *
 * Unlike [SonaIconButtonGroup] the minimum touch target is left alone: a text button is already wider
 * than it, so it only adds height, which never reaches the painted container.
 */
@Composable
fun SonaActionButtonGroup(
    modifier: Modifier = Modifier,
    content: ButtonGroupScope.() -> Unit,
) {
    ButtonGroup(
        // Never reached by two or three short labels. Required by the group all the same.
        overflowIndicator = { menuState ->
            SonaIconButton(
                onClick = menuState::show,
                icon = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.top_bar_more_actions),
            )
        },
        modifier = modifier,
        expandedRatio = SonaComponentStyle.PressedExpandedRatio,
        horizontalArrangement = Arrangement.spacedBy(SonaComponentStyle.ItemSpacing),
        content = content,
    )
}

/**
 * One button in a [SonaActionButtonGroup], sharing its press with the group so the button morphs
 * and the group widens it from the same touch. Declared in order: the way out first, the action last.
 */
fun ButtonGroupScope.actionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) = customItem(
    buttonGroupContent = {
        val interactionSource = remember { MutableInteractionSource() }
        TextButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(
                shape = CircleShape,
                pressedShape = SonaComponentStyle.Shape,
            ),
            modifier = Modifier.animateWidth(interactionSource),
            enabled = enabled,
            interactionSource = interactionSource,
        ) {
            Text(label)
        }
    },
    menuContent = { menuState ->
        DropdownMenuItem(
            text = { Text(label) },
            onClick = {
                menuState.dismiss()
                onClick()
            },
            enabled = enabled,
        )
    },
)
