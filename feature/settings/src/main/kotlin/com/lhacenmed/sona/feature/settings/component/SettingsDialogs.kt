package com.lhacenmed.sona.feature.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialogOption
import com.lhacenmed.sona.feature.settings.R

/**
 * The chooser behind a [SettingsChoiceItem].
 *
 * Picking an option is the whole interaction, so there is no confirm button - only a way out.
 */
@Composable
internal fun SettingsChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Read here rather than inside the group: a group builds its items outside composition.
    val cancelLabel = stringResource(R.string.dialog_cancel)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = { actionButton(label = cancelLabel, onClick = onDismiss) },
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            options.forEachIndexed { index, option ->
                SonaDialogOption(label = option, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
    }
}

/** The editor behind a [SettingsTextFieldItem]. */
@Composable
internal fun SettingsTextFieldDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val cancelLabel = stringResource(R.string.dialog_cancel)
    val saveLabel = stringResource(R.string.dialog_save)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, onClick = { onConfirm(value) })
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
        )
    }
}
