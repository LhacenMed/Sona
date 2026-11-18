package com.lhacenmed.sona.core.designsystem.component.dialog

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.toast

/**
 * The one confirmation dialog in the app: it asks before a change, then stays over it until it has
 * finished - excluding folders, deleting playlists, adding or removing a playlist's tracks, clearing
 * the lyrics cache.
 *
 * [title] names the change and how much it touches - "Remove 3 tracks" - so [message] only says what
 * follows from it. Confirming starts [operation], which reports back whether it succeeded. Until it
 * does, the dialog shows it is working - none of these changes can tell how far along it is - both
 * buttons are held and the dialog cannot be dismissed, so nothing reads as done before it is. Once it
 * reports, a toast says how it went and the dialog closes.
 */
@Composable
fun SonaConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    successMessage: String,
    failureMessage: String,
    onDismiss: () -> Unit,
    operation: (onFinished: (succeeded: Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }
    // Read here rather than inside the group: a group builds its items outside composition.
    val cancelLabel = stringResource(R.string.confirmation_dialog_cancel)

    SonaDialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        title = title,
        isWorking = isRunning,
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss, enabled = !isRunning)
            actionButton(
                label = confirmLabel,
                onClick = {
                    isRunning = true
                    operation { succeeded ->
                        val outcome = if (succeeded) successMessage else failureMessage
                        context.toast(outcome)
                        onDismiss()
                    }
                },
                enabled = !isRunning,
            )
        },
    ) {
        Text(message)
    }
}
