package com.lhacenmed.sona.core.designsystem.component.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialogOption
import com.lhacenmed.sona.core.designsystem.R

/**
 * The chooser behind a [SettingsChoiceItem]: every option by its name, in its own order - or, for a look,
 * the option at [defaultIndex], Sona's own, first and named Default, and the rest after it - under its row's
 * [icon], centred over the title, where it has one.
 *
 * Picking an option is the whole interaction, so there is no confirm button - only a way out.
 */
@Composable
fun SettingsChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    defaultIndex: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
) {
    // Read here rather than inside the group: a group builds its items outside composition.
    val cancelLabel = stringResource(R.string.confirmation_dialog_cancel)
    val defaultLabel = settingsDefaultLabel()
    val order = remember(options.size, defaultIndex) { listOfNotNull(defaultIndex) + options.indices.filter { it != defaultIndex } }

    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
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

/** What a look's chooser calls Sona's own look, whatever it is - see [SettingsChoiceDialog]. */
@Composable
fun settingsDefaultLabel(): String = stringResource(R.string.settings_row_default)

/**
 * The editor behind a [SettingsTextFieldItem] - laid out as Seal's are: its row's [icon] centred over the title, what the
 * setting does in [description], then the field, named by [label] and ended by [suffix] - a unit, say - with [keyboardType]'s
 * keys. A value [isValid] refuses is not saved: the field says so, and the dialog stays open. Where it has a [check] - a key
 * tried against its service, say - Save runs it while the dialog shows it is working, and saves only what it holds: what it
 * answers instead is shown on the field, in the colour of an error.
 */
@Composable
fun SettingsTextFieldDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    description: String? = null,
    label: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isValid: (String) -> Boolean = { true },
    check: (suspend (String) -> String?)? = null,
) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(initialValue) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    val invalidLabel = stringResource(R.string.settings_dialog_invalid)
    val cancelLabel = stringResource(R.string.confirmation_dialog_cancel)
    val saveLabel = stringResource(R.string.settings_dialog_save)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        isWorking = isChecking,
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, enabled = !isChecking, onClick = {
                when {
                    !isValid(value) -> refusal = invalidLabel
                    check == null -> onConfirm(value)
                    else -> {
                        isChecking = true
                        scope.launch {
                            val reason = check(value)
                            isChecking = false
                            if (reason == null) onConfirm(value) else refusal = reason
                        }
                    }
                }
            })
        },
    ) {
        description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                refusal = null
            },
            enabled = !isChecking,
            label = label?.let { { Text(it) } },
            trailingIcon = suffix?.let { { Text(it) } },
            isError = refusal != null,
            supportingText = refusal?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(top = if (description != null) 16.dp else 0.dp),
        )
    }
}
