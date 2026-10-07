package com.lhacenmed.sona.core.designsystem.component.fab

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.pressedCornerRadius
import com.lhacenmed.sona.core.designsystem.theme.rememberPressFraction
import com.lhacenmed.sona.core.designsystem.theme.roundedShape

/** How tall an extended FAB is: Material's FAB. */
internal val ExtendedButtonHeight = 56.dp

/** The corners an extended FAB rests with: Material's FAB corner. */
private val ExtendedButtonCornerRadius = 16.dp

/** One of a screen's labelled FABs - see [SonaExtendedFloatingActionButtons]. */
class ExtendedFloatingActionButtonContent(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    /** The screen's main action, filled with the primary colour; the others stand back on a surface. */
    val isPrimary: Boolean = false,
)

/** A screen's labelled FABs as [FloatingActionButtonStack] holds them: handed over once, read where it draws them. */
internal class ExtendedButtons(private val latestButtons: State<List<ExtendedFloatingActionButtonContent>>) : ScreenButtons {
    val buttons: List<ExtendedFloatingActionButtonContent>
        get() = latestButtons.value

    override val height: Dp
        get() = if (buttons.isEmpty()) 0.dp else ExtendedButtonHeight * buttons.size + StackSpacing * (buttons.size - 1)
}

/**
 * A screen's own FABs where each says what it does - Material's extended FAB - stacked up the end edge in
 * [buttons]' order, standing at the bottom of the screen's [FloatingActionButtonStack]: drawn over the whole
 * window, on the player, and stepped aside with the rest of the stack - so the screen needs no room of its own
 * for them. A screen has these or a [SonaFloatingActionButtonMenu], not both.
 */
@Composable
fun SonaExtendedFloatingActionButtons(buttons: List<ExtendedFloatingActionButtonContent>) {
    val stack = checkNotNull(LocalFloatingActionButtonStack.current) { "A FAB needs a FloatingActionButtonStack" }
    val latestButtons = rememberUpdatedState(buttons)
    val holder = remember { ExtendedButtons(latestButtons) }
    DisposableEffect(stack, holder) {
        stack.screenButtons = holder
        onDispose { if (stack.screenButtons === holder) stack.screenButtons = null }
    }
}

/** [buttons] as the stack draws them, at [anchor] - shown while the stack [isShown]. */
@Composable
internal fun ExtendedButtonColumn(buttons: List<ExtendedFloatingActionButtonContent>, isShown: Boolean, anchor: Modifier) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(StackSpacing),
        modifier = anchor,
    ) {
        buttons.forEach { button -> ExtendedButton(button, visible = isShown) }
    }
}

/** One extended FAB, pressing as every press in Sona does. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExtendedButton(button: ExtendedFloatingActionButtonContent, visible: Boolean) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressFraction by rememberPressFraction(interactionSource)
    ExtendedFloatingActionButton(
        text = { Text(button.label) },
        icon = { Icon(imageVector = button.icon, contentDescription = null) },
        onClick = button.onClick,
        modifier = Modifier.animateFloatingActionButton(visible = visible, alignment = Alignment.CenterEnd),
        shape = roundedShape(pressedCornerRadius(pressFraction, restingRadius = ExtendedButtonCornerRadius)),
        containerColor = if (button.isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (button.isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        interactionSource = interactionSource,
    )
}
