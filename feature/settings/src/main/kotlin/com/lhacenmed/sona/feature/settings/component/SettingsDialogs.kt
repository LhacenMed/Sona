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
 * The chooser behind a [SettingsChoiceItem]: the option at [defaultIndex] - what the setting starts at -
 * first, named Default, and the rest after it in their own order.
 *
 * Picking an option is the whole interaction, so there is no confirm button - only a way out.
 */
@Composable
internal fun SettingsChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    defaultIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Read here rather than inside the group: a group builds its items outside composition.
    val cancelLabel = stringResource(R.string.dialog_cancel)
    val defaultLabel = settingsDefaultLabel()
    val order = remember(options.size, defaultIndex) { listOf(defaultIndex) + options.indices.filter { it != defaultIndex } }

    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = { actionButton(label = cancelLabel, onClick = onDismiss) },
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            order.forEach { index ->
                SonaDialogOption(
                    label = if (index == defaultIndex) defaultLabel else options[index],
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/** What every chooser calls the option its setting starts at, whatever that option is. */
@Composable
internal fun settingsDefaultLabel(): String = stringResource(R.string.settings_option_default)

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
