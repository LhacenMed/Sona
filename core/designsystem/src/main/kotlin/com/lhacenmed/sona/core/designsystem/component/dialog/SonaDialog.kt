package com.lhacenmed.sona.core.designsystem.component.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.effect.ProvideSonaHaptics

/** Material's widest dialog, so one never stretches across a tablet or a landscape screen. */
private val DialogMaxWidth = 560.dp

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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = modifier.heightIn(max = maxHeight).widthIn(max = DialogMaxWidth),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        DialogHeader(title = title, icon = icon)
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
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
}

/**
 * One choice in a dialog's list of them - a radio button and its label, the whole row the target.
 * The rows go in a column marked `selectableGroup`, so they are read out as one set.
 */
@Composable
fun SonaDialogOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 16.dp))
    }
}

/** The icon over the title, both centred; with no icon, the title alone at the start, as Material has it. */
@Composable
private fun ColumnScope.DialogHeader(title: String?, icon: (@Composable () -> Unit)?) {
    if (icon != null) {
        CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.iconContentColor) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) { icon() }
        }
    }
    if (title != null) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = AlertDialogDefaults.titleContentColor,
            modifier = Modifier
                .align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)
                .padding(bottom = 16.dp),
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
            .padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onReset != null) {
            SonaActionButtonGroup { actionButton(label = resetLabel, onClick = onReset) }
        }
        Spacer(modifier = Modifier.weight(1f))
        SonaActionButtonGroup(content = buttons)
    }
}
