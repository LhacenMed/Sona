package com.lhacenmed.sona.feature.settings.backup

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.backup.BackupSchedule
import com.lhacenmed.sona.feature.backup.work.BackupRun
import com.lhacenmed.sona.feature.backup.work.backupProgressText
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsInfoItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNote
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem

/**
 * Backing the library up, and restoring it: a backup now or on a schedule, optionally encrypted, kept in Sona's
 * folder; and a restore from any of those, or from a backup file anywhere on the device.
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
        val backups by viewModel.backups.collectAsStateWithLifecycle()
        val restoreStep by viewModel.restoreStep.collectAsStateWithLifecycle()
        var isSettingPassphrase by rememberSaveable { mutableStateOf(false) }
        val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::open)
        }
        val isBackingUp = backupRun is BackupRun.Running
        val isRestoring = restoreRun is BackupRun.Running

        RunOutcomeToast(backupRun, successRes = R.string.backup_done, failureRes = R.string.backup_failed)
        RunOutcomeToast(restoreRun, successRes = R.string.restore_done, failureRes = R.string.restore_failed)

        SettingsList {
            SettingsSection(stringResource(R.string.backup_section)) {
                SettingsActionItem(
                    title = stringResource(R.string.backup_now_title),
                    summary = if (isBackingUp) {
                        runText(context, backupRun, isRestore = false)
                    } else {
                        lastBackupAt?.let { stringResource(R.string.backup_last, formatDate(context, it)) } ?: stringResource(R.string.backup_none)
                    },
                    onClick = viewModel::backUpNow,
                    enabled = !isBackingUp,
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
                    summary = stringResource(R.string.backup_encrypt_summary),
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
                SettingsInfoItem(
                    title = stringResource(R.string.backup_location_title),
                    value = viewModel.location,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.restore_section)) {
                SettingsActionItem(
                    title = stringResource(R.string.restore_from_file_title),
                    summary = if (isRestoring) runText(context, restoreRun, isRestore = true) else stringResource(R.string.restore_from_file_summary),
                    onClick = { pickBackup.launch(arrayOf("*/*")) },
                    enabled = !isRestoring,
                    isBusy = isRestoring,
                )
                backups.forEach { backup ->
                    SettingsActionItem(
                        title = formatDate(context, backup.createdAt),
                        summary = Formatter.formatShortFileSize(context, backup.sizeBytes),
                        onClick = { viewModel.open(backup) },
                        enabled = !isRestoring,
                    )
                }
                SettingsNote(stringResource(R.string.backup_note))
            }
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

private val BackupSchedule.labelRes: Int
    @StringRes get() = when (this) {
        BackupSchedule.OFF -> R.string.backup_schedule_off
        BackupSchedule.DAILY -> R.string.backup_schedule_daily
        BackupSchedule.WEEKLY -> R.string.backup_schedule_weekly
    }

/** Tells how a run the screen saw going ended - only one it saw going, not one that ended before it opened. */
@Composable
private fun RunOutcomeToast(run: BackupRun?, @StringRes successRes: Int, @StringRes failureRes: Int) {
    val context = LocalContext.current
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(run) {
        if (wasRunning) {
            when (run) {
                BackupRun.Succeeded -> context.toast(successRes)
                BackupRun.Failed -> context.toast(failureRes)
                else -> Unit
            }
        }
        wasRunning = run is BackupRun.Running
    }
}

private fun runText(context: Context, run: BackupRun?, isRestore: Boolean): String =
    backupProgressText(context, (run as? BackupRun.Running)?.progress, isRestore).orEmpty()

internal fun formatDate(context: Context, epochMs: Long): String =
    DateUtils.formatDateTime(context, epochMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME)
