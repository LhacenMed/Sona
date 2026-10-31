package com.lhacenmed.sona.feature.settings.backup

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.feature.backup.BackupFolder
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
 * What "Back up now" is about to do, at a glance: where the backup is saved, whose music and videos go to Google
 * Drive with it, and over which connection.
 */
@Composable
internal fun BackupNowDialog(
    isDriveConnected: Boolean,
    folders: List<BackupFolder>?,
    usesMobileData: Boolean,
    onBackUp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelLabel = stringResource(R.string.dialog_cancel)
    val backUpLabel = stringResource(R.string.backup_now_action)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.backup_now_title),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = backUpLabel, onClick = onBackUp)
        },
    ) {
        Fact(
            label = stringResource(R.string.backup_now_saved_to),
            value = stringResource(if (isDriveConnected) R.string.backup_now_device_and_drive else R.string.backup_now_device),
        )
        if (isDriveConnected) {
            Fact(stringResource(R.string.backup_now_media), foldersSummary(folders).orEmpty())
            Fact(
                label = stringResource(R.string.backup_now_upload_over),
                value = stringResource(if (usesMobileData) R.string.backup_now_any_network else R.string.backup_now_wifi),
            )
        }
    }
}

/** Asks before stopping a backup under way. */
@Composable
internal fun StopBackupDialog(onPause: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit) {
    val keepGoingLabel = stringResource(R.string.backup_stop_keep_going)
    val pauseLabel = stringResource(R.string.backup_stop_pause)
    val stopLabel = stringResource(R.string.backup_stop_action)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.backup_stop_title),
        buttons = {
            actionButton(label = keepGoingLabel, onClick = onDismiss)
            actionButton(label = pauseLabel, onClick = onPause)
            actionButton(label = stopLabel, onClick = onStop)
        },
    ) {
        Text(stringResource(R.string.backup_stop_message))
    }
}

/** Asks where to delete [backup] from - each place it is kept, all chosen to begin with. */
@Composable
internal fun DeleteBackupDialog(
    backup: RestorableBackup,
    onDelete: (fromDevice: Boolean, fromDrive: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var fromDevice by remember { mutableStateOf(backup.onDevice != null) }
    var fromDrive by remember { mutableStateOf(backup.inDrive != null) }
    val cancelLabel = stringResource(R.string.dialog_cancel)
    val deleteLabel = stringResource(R.string.backup_delete_action)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.backup_delete_title),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = deleteLabel, onClick = { onDelete(fromDevice, fromDrive) }, enabled = fromDevice || fromDrive)
        },
    ) {
        Text(formatDate(LocalContext.current, backup.createdAt))
        if (backup.onDevice != null) CheckRow(stringResource(R.string.backup_delete_from_device), fromDevice) { fromDevice = it }
        if (backup.inDrive != null) CheckRow(stringResource(R.string.backup_delete_from_drive), fromDrive) { fromDrive = it }
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
        Fact(stringResource(R.string.restore_made), formatDate(context, preview.createdAt))
        Fact(stringResource(R.string.restore_playlists), preview.playlistCount.toString())
        Fact(stringResource(R.string.restore_played_tracks), preview.playedTrackCount.toString())
        Fact(stringResource(R.string.restore_files_here), media.present.size.toString())
        if (media.unavailable.isNotEmpty()) Fact(stringResource(R.string.restore_files_lost), media.unavailable.size.toString())
        if (media.missing.isNotEmpty()) {
            Text(
                text = stringResource(R.string.restore_files_from_drive),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            media.missing.forEach { missing ->
                val fingerprint = missing.media.fingerprint
                CheckRow(missing.media.path.substringAfterLast('/'), fingerprint in filesToRestore) { chosen ->
                    filesToRestore = if (chosen) filesToRestore + fingerprint else filesToRestore - fingerprint
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

/**
 * What [folders] back up to Drive, in a word or two: all of them, none, or how many - each folder counted once, its
 * music and its videos alike, however many of them are chosen. Null while they are read.
 */
@Composable
internal fun foldersSummary(folders: List<BackupFolder>?): String? {
    val all = folders ?: return null
    val backedUpPaths = all.filter { it.isBackedUp }.mapTo(HashSet()) { it.path }
    return when {
        backedUpPaths.isEmpty() -> stringResource(R.string.backup_folders_none)
        all.all { it.isBackedUp } -> stringResource(R.string.backup_folders_all)
        else -> pluralStringResource(R.plurals.backup_folders_count, backedUpPaths.size, backedUpPaths.size)
    }
}

/** One thing a dialog says, as a label and its value on one line. */
@Composable
private fun Fact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** A choice in a dialog, checked or not, the whole row its target. */
@Composable
private fun CheckRow(label: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(isChecked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Checkbox(checked = isChecked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
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
