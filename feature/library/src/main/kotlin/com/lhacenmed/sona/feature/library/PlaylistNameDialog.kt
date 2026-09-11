package com.lhacenmed.sona.feature.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Asks for a playlist's name, whether it is being created or renamed.
 *
 * The two rules are the reference app's: a name cannot be blank, and cannot be one already in use.
 * Both are checked as the user types and shown on the field rather than as a toast after the fact,
 * so the confirm button simply stays unavailable until the name is one that will work - the
 * database has the same unique rule, and this keeps the user from meeting it as a failure.
 */
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
    val isTaken = takenNames.any { it.equals(trimmed, ignoreCase = true) }
    val error = when {
        trimmed.isEmpty() -> null
        isTaken -> "A playlist with that name already exists"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty() && !isTaken,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
