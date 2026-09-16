package com.lhacenmed.sona.feature.library.operation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.feature.library.options.toast

/** The most of [ConfirmedOperationDialog]'s subject list shown before it scrolls. */
private val SubjectsMaxHeight = 240.dp

/** A progress bar's own height: the slot held for it before it appears. */
private val ProgressSlotHeight = 4.dp

/**
 * Asks before a change that cannot be taken back, then stays over it until it has finished - the one
 * shape every destructive library change takes: excluding folders, deleting playlists, removing
 * tracks from a playlist.
 *
 * [subjects] names exactly what will change. Confirming starts [operation], which reports back whether
 * it succeeded. Until it does, an indeterminate progress bar runs - none of these changes can tell how
 * far along it is - both buttons are held and the dialog cannot be dismissed, so nothing reads as done
 * before it is. Once it reports, a toast says how it went and the dialog closes.
 *
 * The progress bar's slot is held from the first frame, so confirming moves nothing in the dialog.
 */
@Composable
internal fun ConfirmedOperationDialog(
    title: String,
    message: String,
    subjects: List<String>,
    confirmLabel: String,
    successMessage: String,
    failureMessage: String,
    onDismiss: () -> Unit,
    operation: (onFinished: (succeeded: Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = SubjectsMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    subjects.forEach { subject ->
                        Text(
                            text = subject,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .heightIn(min = ProgressSlotHeight),
                ) {
                    if (isRunning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            SonaActionButtonGroup {
                actionButton(label = "Cancel", onClick = onDismiss, enabled = !isRunning)
                actionButton(
                    label = confirmLabel,
                    onClick = {
                        isRunning = true
                        operation { succeeded ->
                            context.toast(if (succeeded) successMessage else failureMessage)
                            onDismiss()
                        }
                    },
                    enabled = !isRunning,
                )
            }
        },
    )
}
