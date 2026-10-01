package com.lhacenmed.sona.core.designsystem.component.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.effect.ProvideSonaHaptics

/** Material's widest dialog, so one never stretches across a tablet or a landscape screen. */
private val DialogMaxWidth = 560.dp

/** The room a dialog leaves between itself and the window's edges. */
private val DialogMargin = 24.dp

/** How far everything in a dialog stands in from its edges - all but an option's press, which reaches them. */
private val DialogPadding = 24.dp

/**
 * The one dialog in the app - ArchiveTune's `DefaultDialog`: a surface in Material's dialog shape and
 * colour, [icon] centred over [title] when there is one, [content], then [buttons] at the end.
 *
 * [buttons] are a [SonaActionButtonGroup]'s, declared as it declares them: the way out first, the
 * action last. [onReset], for a dialog that edits a value, adds Reset at the start of the row, apart
 * from the rest - where ArchiveTune puts it - since it answers the dialog rather than closing it.
 *
 * The title and the buttons stay put while [content] scrolls, so a dialog is never taller than the
 * screen and its buttons are never out of reach - with the keyboard up, too.
 *
 * A press anywhere off it dismisses it, as back does: the dialog is all its window holds - see
 * [dialogSize] - so everywhere else is outside it.
 */
@Composable
fun SonaDialog(
    onDismissRequest: () -> Unit,
    buttons: ButtonGroupScope.() -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Its own window, with its own haptics: gated as every window's are.
        ProvideSonaHaptics {
            Surface(
                modifier = modifier.dialogSize(),
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(modifier = Modifier.padding(vertical = DialogPadding)) {
                    DialogHeader(title = title, icon = icon)
                    // The scroll spans the dialog's whole width and its content stands in from inside it, so a
                    // row can reach the dialog's edges - see [SonaDialogOption] - without being cut off.
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DialogPadding),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                            content()
                        }
                    }
                    DialogButtons(buttons = buttons, onReset = onReset)
                }
            }
        }
    }
}

/**
 * As wide as its window allows less [DialogMargin] each side, no wider than [DialogMaxWidth], and no taller
 * than the window less its margins - its content scrolling past that. The margins are left out of its size,
 * not padded around it: the window centres what it holds, and a press anywhere off the dialog itself is
 * outside it.
 */
private fun Modifier.dialogSize(): Modifier = layout { measurable, constraints ->
    val margin = DialogMargin.roundToPx()
    val width = minOf(constraints.maxWidth - 2 * margin, DialogMaxWidth.roundToPx()).coerceAtLeast(0)
    val maxHeight = if (constraints.hasBoundedHeight) (constraints.maxHeight - 2 * margin).coerceAtLeast(0) else Constraints.Infinity
    val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = maxHeight))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * One choice in a dialog's list of them - a radio button and its label, the whole row the target, its press
 * reaching the dialog's edges as a list row's does while what it holds stays in line with the rest. The rows
 * go in a column marked `selectableGroup`, so they are read out as one set.
 */
@Composable
fun SonaDialogOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .acrossDialog()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = DialogPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 16.dp))
    }
}

/**
 * Spreads what is laid out within the dialog's padding over it, out to the dialog's edges either side - for a
 * row whose press shows across the whole dialog, which then stands its own content in by [DialogPadding].
 */
private fun Modifier.acrossDialog(): Modifier = layout { measurable, constraints ->
    val inset = DialogPadding.roundToPx()
    val width = constraints.maxWidth + 2 * inset
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-inset, 0) }
}

/** The icon over the title, both centred; with no icon, the title alone at the start, as Material has it. */
@Composable
private fun ColumnScope.DialogHeader(title: String?, icon: (@Composable () -> Unit)?) {
    if (icon != null) {
        CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.iconContentColor) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).padding(start = DialogPadding, end = DialogPadding, bottom = 16.dp)) {
                icon()
            }
        }
    }
    if (title != null) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = AlertDialogDefaults.titleContentColor,
            modifier = Modifier
                .align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)
                .padding(start = DialogPadding, end = DialogPadding, bottom = 16.dp),
        )
    }
}

@Composable
private fun DialogButtons(buttons: ButtonGroupScope.() -> Unit, onReset: (() -> Unit)?) {
    // Read here rather than inside the group: a group builds its items outside composition.
    val resetLabel = stringResource(R.string.dialog_reset)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = DialogPadding, end = DialogPadding, top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onReset != null) {
            SonaActionButtonGroup { actionButton(label = resetLabel, onClick = onReset) }
        }
        Spacer(modifier = Modifier.weight(1f))
        SonaActionButtonGroup(content = buttons)
    }
}
