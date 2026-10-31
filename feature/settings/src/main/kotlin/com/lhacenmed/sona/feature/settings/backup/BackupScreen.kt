package com.lhacenmed.sona.feature.settings.backup

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.backup.BackupFolders
import com.lhacenmed.sona.feature.backup.BackupOutcome
import com.lhacenmed.sona.feature.explorer.FolderPickerSheet
import com.lhacenmed.sona.feature.explorer.FolderPickerTab
import com.lhacenmed.sona.feature.backup.BackupSchedule
import com.lhacenmed.sona.feature.backup.work.BackupFailure
import com.lhacenmed.sona.feature.backup.work.BackupRun
import com.lhacenmed.sona.feature.backup.work.backupProgressSummary
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsLoad
import com.lhacenmed.sona.feature.settings.component.SettingsNote
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem

/**
 * Backing the library up, and restoring it: a backup now or on a schedule, optionally encrypted, kept in Sona's
 * folder and - once connected - in Google Drive with the library's files; and a restore from any of those, or from
 * a backup file anywhere on the device.
 */
data object BackupScreen : Screen {
    override val titleRes: Int get() = R.string.backup_title

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val viewModel: BackupViewModel = hiltViewModel()
        val schedule by viewModel.schedule.collectAsStateWithLifecycle()
        val isEncrypted by viewModel.isEncrypted.collectAsStateWithLifecycle()
        val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
        val backupRun by viewModel.backupRun.collectAsStateWithLifecycle()
        val restoreRun by viewModel.restoreRun.collectAsStateWithLifecycle()
        val backups by viewModel.restorableBackups.collectAsStateWithLifecycle()
        val restoreStep by viewModel.restoreStep.collectAsStateWithLifecycle()
        val isDriveConnected by viewModel.isDriveConnected.collectAsStateWithLifecycle()
        val driveAccount by viewModel.driveAccount.collectAsStateWithLifecycle()
        val driveBackups by viewModel.driveBackupList.collectAsStateWithLifecycle()
        val folders by viewModel.folders.collectAsStateWithLifecycle()
        val filePicks by viewModel.filePicks.collectAsStateWithLifecycle()
        val usesMobileData by viewModel.usesMobileData.collectAsStateWithLifecycle()
        var isSettingPassphrase by rememberSaveable { mutableStateOf(false) }
        var isConfirmingBackup by rememberSaveable { mutableStateOf(false) }
        var isStoppingBackup by rememberSaveable { mutableStateOf(false) }
        var isChoosingFolders by rememberSaveable { mutableStateOf(false) }
        var backupToDelete by remember { mutableStateOf<RestorableBackup?>(null) }
        var isDisconnectingDrive by rememberSaveable { mutableStateOf(false) }
        val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::open)
        }
        val onDriveConnectFailed = { context.toast(R.string.backup_drive_connect_failed) }
        val consentToDrive = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            viewModel.finishConnectingDrive(result.data, onDriveConnectFailed)
        }
        val isBackingUp = backupRun is BackupRun.Running
        val isBackupPaused = backupRun is BackupRun.Paused
        val isRestoring = restoreRun is BackupRun.Running

        RunOutcomeToast(backupRun, successRes = R.string.backup_done, failureRes = R.string.backup_failed)
        RunOutcomeToast(restoreRun, successRes = R.string.restore_done, failureRes = R.string.restore_failed)
        // Over - with this screen open or not - so nothing is left asking about it.
        if (!isBackingUp) isStoppingBackup = false

        SettingsList {
            SettingsSection(stringResource(R.string.backup_section)) {
                SettingsActionItem(
                    title = stringResource(R.string.backup_now_title),
                    summary = when {
                        isBackingUp -> runText(context, backupRun)
                        isBackupPaused -> stringResource(R.string.backup_paused)
                        else -> lastBackupAt?.let { stringResource(R.string.backup_last, formatDate(context, it)) } ?: stringResource(R.string.backup_none)
                    },
                    onClick = {
                        when {
                            isBackingUp -> isStoppingBackup = true
                            isBackupPaused -> viewModel.resumeBackup()
                            else -> isConfirmingBackup = true
                        }
                    },
                    isBusy = isBackingUp,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.backup_schedule_title),
                    options = BackupSchedule.entries.map { stringResource(it.labelRes) },
                    selectedIndex = schedule.ordinal,
                    onSelect = { viewModel.setSchedule(BackupSchedule.entries[it]) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.backup_encrypt_title),
                    checked = isEncrypted,
                    onCheckedChange = { encrypt ->
                        if (encrypt) {
                            isSettingPassphrase = true
                        } else {
                            viewModel.setPassphrase(null) { succeeded ->
                                context.toast(if (succeeded) R.string.backup_encryption_off else R.string.backup_passphrase_failed)
                            }
                        }
                    },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.backup_drive_title),
                    summary = driveAccount,
                    checked = isDriveConnected,
                    onCheckedChange = { connect ->
                        if (!connect) {
                            isDisconnectingDrive = true
                        } else {
                            viewModel.connectDrive(
                                onConsent = { consentToDrive.launch(IntentSenderRequest.Builder(it).build()) },
                                onFailed = onDriveConnectFailed,
                            )
                        }
                    },
                )
                SettingsActionItem(
                    title = stringResource(R.string.backup_folders_title),
                    summary = foldersSummary(folders),
                    onClick = { isChoosingFolders = true },
                    enabled = isDriveConnected,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.backup_mobile_data_title),
                    checked = usesMobileData,
                    onCheckedChange = viewModel::setUsesMobileData,
                    enabled = isDriveConnected,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.restore_section)) {
                SettingsActionItem(
                    title = stringResource(R.string.restore_from_file_title),
                    summary = if (isRestoring) runText(context, restoreRun) else null,
                    onClick = { pickBackup.launch(arrayOf("*/*")) },
                    enabled = !isRestoring,
                    isBusy = isRestoring,
                )
                backups.forEach { backup ->
                    RestorableBackupItem(
                        backup = backup,
                        onClick = { viewModel.open(backup) },
                        onLongClick = { backupToDelete = backup },
                        enabled = !isRestoring,
                    )
                }
                if (driveBackups == SettingsLoad.Failed) {
                    SettingsActionItem(
                        title = stringResource(R.string.restore_drive_failed),
                        summary = stringResource(R.string.restore_drive_failed_summary),
                        onClick = viewModel::retryDriveBackups,
                    )
                }
                SettingsNote(stringResource(R.string.backup_note))
            }
        }

        if (isConfirmingBackup) {
            BackupNowDialog(
                isDriveConnected = isDriveConnected,
                folders = folders,
                usesMobileData = usesMobileData,
                onBackUp = {
                    isConfirmingBackup = false
                    viewModel.backUpNow()
                },
                onDismiss = { isConfirmingBackup = false },
            )
        }

        backupToDelete?.let { backup ->
            DeleteBackupDialog(
                backup = backup,
                onDelete = { fromDevice, fromDrive ->
                    backupToDelete = null
                    viewModel.delete(backup, fromDevice, fromDrive) { succeeded ->
                        context.toast(if (succeeded) R.string.backup_deleted else R.string.backup_delete_failed)
                    }
                },
                onDismiss = { backupToDelete = null },
            )
        }

        if (isChoosingFolders) {
            filePicks?.let { files ->
                FolderPickerSheet(
                    title = stringResource(R.string.backup_folders_title),
                    // A kind with no file has no tab: there is nothing to pick under it.
                    tabs = listOf(
                        FolderPickerTab(stringResource(R.string.backup_folders_music), files.music, BackupFolders.MUSIC_KEY_PREFIX),
                        FolderPickerTab(stringResource(R.string.backup_folders_videos), files.videos, BackupFolders.VIDEOS_KEY_PREFIX),
                    ).filter { it.files.isNotEmpty() },
                    initiallyPicked = files.picked,
                    confirmLabel = { context.getString(R.string.backup_folders_save) },
                    onConfirm = viewModel::backUpFolders,
                    onDismissRequest = { isChoosingFolders = false },
                )
            }
        }

        if (isStoppingBackup) {
            StopBackupDialog(
                onPause = {
                    isStoppingBackup = false
                    viewModel.pauseBackup()
                },
                onStop = {
                    isStoppingBackup = false
                    viewModel.stopBackup()
                },
                onDismiss = { isStoppingBackup = false },
            )
        }

        if (isDisconnectingDrive) {
            SonaConfirmationDialog(
                title = stringResource(R.string.backup_drive_disconnect_title),
                message = stringResource(R.string.backup_drive_disconnect_message),
                confirmLabel = stringResource(R.string.backup_drive_disconnect_action),
                successMessage = stringResource(R.string.backup_drive_disconnected),
                failureMessage = stringResource(R.string.backup_drive_disconnect_failed),
                onDismiss = { isDisconnectingDrive = false },
                operation = viewModel::disconnectDrive,
            )
        }

        if (isSettingPassphrase) {
            NewPassphraseDialog(
                onConfirm = { passphrase ->
                    isSettingPassphrase = false
                    viewModel.setPassphrase(passphrase) { succeeded ->
                        context.toast(if (succeeded) R.string.backup_passphrase_set else R.string.backup_passphrase_failed)
                    }
                },
                onDismiss = { isSettingPassphrase = false },
            )
        }

        restoreStep?.let { step ->
            RestoreDialog(
                step = step,
                onPassphrase = viewModel::submitPassphrase,
                onRestore = viewModel::restore,
                onDismiss = viewModel::dismissRestore,
            )
        }
    }
}

/**
 * A backup in the restore list, with where it is kept - this device, Google Drive, or both - shown at its end. A
 * press restores it; a long press offers to delete it.
 */
@Composable
private fun RestorableBackupItem(backup: RestorableBackup, onClick: () -> Unit, onLongClick: () -> Unit, enabled: Boolean) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(formatDate(context, backup.createdAt)) },
        supportingContent = { Text(Formatter.formatShortFileSize(context, backup.sizeBytes)) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (backup.onDevice != null) Icon(Icons.Filled.Smartphone, contentDescription = stringResource(R.string.restore_on_device))
                if (backup.inDrive != null) Icon(Icons.Filled.Cloud, contentDescription = stringResource(R.string.restore_in_drive))
            }
        },
        modifier = Modifier.combinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = onClick),
    )
}

/** What a backup that ended so says, where it says more than that it was saved. */
private val BackupOutcome.messageRes: Int?
    @StringRes get() = when (this) {
        BackupOutcome.SAVED_FILES_WAITING -> R.string.backup_done_files_waiting
        BackupOutcome.SAVED_FILES_LEFT -> R.string.backup_done_files_left
        BackupOutcome.SAVED_DRIVE_FULL -> R.string.backup_done_drive_full
        BackupOutcome.UNCHANGED -> R.string.backup_done_unchanged
        BackupOutcome.SAVED -> null
    }

private val BackupSchedule.labelRes: Int
    @StringRes get() = when (this) {
        BackupSchedule.OFF -> R.string.backup_schedule_off
        BackupSchedule.DAILY -> R.string.backup_schedule_daily
        BackupSchedule.WEEKLY -> R.string.backup_schedule_weekly
    }

/**
 * Tells how a run the screen saw going ended - only one it saw going, not one that ended before it opened - and,
 * for a failure the user can do something about, what.
 */
@Composable
private fun RunOutcomeToast(run: BackupRun?, @StringRes successRes: Int, @StringRes failureRes: Int) {
    val context = LocalContext.current
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(run) {
        if (wasRunning) {
            when (run) {
                is BackupRun.Succeeded -> context.toast(run.outcome?.messageRes ?: successRes)
                BackupRun.Stopped -> context.toast(R.string.backup_stopped)
                BackupRun.Paused -> context.toast(R.string.backup_paused_toast)
                is BackupRun.Failed -> when (run.reason) {
                    BackupFailure.DRIVE_ACCESS_LOST -> context.toast(R.string.backup_failed_drive_access)
                    null -> context.toast(listOfNotNull(context.getString(failureRes), run.detail).joinToString(": "))
                }
                else -> Unit
            }
        }
        wasRunning = run is BackupRun.Running
    }
}

private fun runText(context: Context, run: BackupRun?): String =
    backupProgressSummary(context, (run as? BackupRun.Running)?.progress).orEmpty()

internal fun formatDate(context: Context, epochMs: Long): String =
    DateUtils.formatDateTime(context, epochMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME)
