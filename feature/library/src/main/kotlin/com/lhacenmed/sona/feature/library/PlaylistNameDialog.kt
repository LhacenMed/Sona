package com.lhacenmed.sona.feature.library

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog

/**
 * Asks for a playlist's name, whether it is being created or renamed.
 *
 * The two rules are the reference app's: a name cannot be blank, and cannot be one already in use.
 * Both are checked as the user types and shown on the field rather than as a toast after the fact,
 * so the confirm button simply stays unavailable until the name is one that will work - the
 * database has the same unique rule, and this keeps the user from meeting it as a failure.
 */
/** Whether [name] is already one of [takenNames] - the rule every place a playlist is named keeps. */
internal fun isPlaylistNameTaken(name: String, takenNames: List<String>): Boolean =
    takenNames.any { it.equals(name.trim(), ignoreCase = true) }

internal const val PLAYLIST_NAME_TAKEN_MESSAGE = "A playlist with that name already exists"

@Composable
internal fun PlaylistNameDialog(
    dialogTitle: String,
    confirmLabel: String,
    initialName: String,
    takenNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val isTaken = isPlaylistNameTaken(trimmed, takenNames)
    val error = if (isTaken) PLAYLIST_NAME_TAKEN_MESSAGE else null

    SonaDialog(
        onDismissRequest = onDismiss,
        title = dialogTitle,
        buttons = {
            actionButton(label = "Cancel", onClick = onDismiss)
            actionButton(
                label = confirmLabel,
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty() && !isTaken,
            )
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )
    }
}
