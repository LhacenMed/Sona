package com.lhacenmed.sona.feature.settings.backup

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.feature.backup.RestorePreview
import com.lhacenmed.sona.feature.settings.R

/** Asks for the passphrase backups are encrypted with from now on - twice, so a typo cannot lock them away. */
@Composable
internal fun NewPassphraseDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val isMismatched = repeated.isNotEmpty() && repeated != passphrase
    val cancelLabel = stringResource(R.string.dialog_cancel)
    val saveLabel = stringResource(R.string.dialog_save)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.backup_passphrase_title),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, onClick = { onConfirm(passphrase) }, enabled = passphrase.isNotEmpty() && repeated == passphrase)
        },
    ) {
        Text(stringResource(R.string.backup_passphrase_note))
        PassphraseField(passphrase, { passphrase = it }, stringResource(R.string.backup_passphrase_hint))
        PassphraseField(
            value = repeated,
            onValueChange = { repeated = it },
            label = stringResource(R.string.backup_passphrase_repeat_hint),
            error = stringResource(R.string.backup_passphrase_mismatch).takeIf { isMismatched },
        )
    }
}

/**
 * Restoring a backup, step by step as it is opened: opening it, asking for its passphrase, what restoring it
 * would do - and, of the files this device is missing, the ones to bring back - or why it cannot be.
 */
@Composable
internal fun RestoreDialog(
    step: RestoreStep,
    onPassphrase: (String) -> Unit,
    onRestore: (filesToRestore: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelLabel = stringResource(R.string.dialog_cancel)
    when (step) {
        RestoreStep.Opening -> SonaDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.restore_dialog_title),
            buttons = { actionButton(label = cancelLabel, onClick = onDismiss) },
        ) {
            Text(stringResource(R.string.restore_opening))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }

        is RestoreStep.Passphrase -> {
            var passphrase by remember { mutableStateOf("") }
            val openLabel = stringResource(R.string.restore_ok)
            SonaDialog(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.restore_passphrase_title),
                buttons = {
                    actionButton(label = cancelLabel, onClick = onDismiss)
                    actionButton(label = openLabel, onClick = { onPassphrase(passphrase) }, enabled = passphrase.isNotEmpty())
                },
            ) {
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.backup_passphrase_hint),
                    error = stringResource(R.string.restore_passphrase_wrong).takeIf { step.isWrong },
                )
            }
        }

        is RestoreStep.Ready -> RestorePreviewDialog(step.preview, onRestore, onDismiss)

        is RestoreStep.Problem -> {
            val okLabel = stringResource(R.string.restore_ok)
            SonaDialog(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.restore_dialog_title),
                buttons = { actionButton(label = okLabel, onClick = onDismiss) },
            ) {
                Text(stringResource(step.messageRes))
            }
        }
    }
}

@Composable
private fun RestorePreviewDialog(
    preview: RestorePreview,
    onRestore: (filesToRestore: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val media = preview.media
    var filesToRestore by remember(media) { mutableStateOf(media.missing.map { it.media.fingerprint }.toSet()) }
    val cancelLabel = stringResource(R.string.dialog_cancel)
    val restoreLabel = stringResource(R.string.restore_action)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.restore_dialog_title),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(
                label = restoreLabel,
                onClick = {
                    // Files are written back where they were, which only managing all files allows.
                    if (filesToRestore.isNotEmpty() && !AppPermission.FILE_CHANGES.isGranted(context)) {
                        context.toast(R.string.restore_file_access_needed)
                        context.startActivity(AppPermission.FILE_CHANGES.settingsIntent(context))
                    } else {
                        onRestore(filesToRestore)
                    }
                },
            )
        },
    ) {
        Text(stringResource(R.string.restore_made_on, formatDate(context, preview.createdAt)), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Text(pluralStringResource(R.plurals.restore_playlists, preview.playlistCount, preview.playlistCount))
        Text(pluralStringResource(R.plurals.restore_played_tracks, preview.playedTrackCount, preview.playedTrackCount))
        Text(stringResource(R.string.restore_settings))
        Text(pluralStringResource(R.plurals.restore_files_present, media.present.size, media.present.size))
        if (media.unavailable.isNotEmpty()) {
            Text(pluralStringResource(R.plurals.restore_files_unavailable, media.unavailable.size, media.unavailable.size))
        }
        if (media.missing.isNotEmpty()) {
            Text(
                text = stringResource(R.string.restore_files_to_restore),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            media.missing.forEach { missing ->
                val fingerprint = missing.media.fingerprint
                val isChosen = fingerprint in filesToRestore
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(isChosen, role = Role.Checkbox) { chosen ->
                            filesToRestore = if (chosen) filesToRestore + fingerprint else filesToRestore - fingerprint
                        },
                ) {
                    Checkbox(checked = isChosen, onCheckedChange = null)
                    Text(missing.media.path.substringAfterLast('/'), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Text(
            text = stringResource(R.string.restore_merge_note),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun PassphraseField(value: String, onValueChange: (String) -> Unit, label: String, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
