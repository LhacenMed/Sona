package com.lhacenmed.sona.feature.settings.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.feature.backup.BackupRepository
import com.lhacenmed.sona.feature.backup.BackupSchedule
import com.lhacenmed.sona.feature.backup.BackupSettings
import com.lhacenmed.sona.feature.backup.BackupFolder
import com.lhacenmed.sona.feature.backup.BackupFilePicks
import com.lhacenmed.sona.feature.backup.BackupFolders
import com.lhacenmed.sona.feature.backup.LocalBackups
import com.lhacenmed.sona.feature.backup.OpenedBackup
import com.lhacenmed.sona.feature.backup.RestorePreview
import com.lhacenmed.sona.feature.backup.StoredBackup
import com.lhacenmed.sona.feature.backup.drive.DriveAccess
import com.lhacenmed.sona.feature.backup.drive.DriveBackups
import com.lhacenmed.sona.feature.backup.drive.DriveConnection
import com.lhacenmed.sona.feature.backup.work.BackupRun
import com.lhacenmed.sona.feature.backup.work.BackupWork
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsLoad
import com.lhacenmed.sona.feature.settings.component.settingsLoadOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

/** Where opening a backup to restore it stands, as its dialog shows it. */
sealed interface RestoreStep {
    data object Opening : RestoreStep

    /** The backup is encrypted with a passphrase this device does not hold - [isWrong] after one that did not open it. */
    data class Passphrase(val isWrong: Boolean) : RestoreStep

    data class Ready(val preview: RestorePreview) : RestoreStep

    data class Problem(@StringRes val messageRes: Int) : RestoreStep
}

/**
 * A backup as the restore list shows it: one row however many places keep it - on this device, in Drive, or both -
 * opened from the device where it is there, which is quicker and needs no connection.
 */
data class RestorableBackup(
    val createdAt: Long,
    val sizeBytes: Long,
    val onDevice: StoredBackup?,
    val inDrive: StoredBackup?,
)

/** The [BackupScreen]'s state: how backups are made, the ones kept here and in Drive, and a backup being restored. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BackupRepository,
    private val backupSettings: BackupSettings,
    private val localBackups: LocalBackups,
    private val driveAccess: DriveAccess,
    private val driveBackups: DriveBackups,
    private val backupFolders: BackupFolders,
) : ViewModel() {

    val schedule: StateFlow<BackupSchedule> = backupSettings.schedule
    val isEncrypted: StateFlow<Boolean> = backupSettings.isEncrypted
    val lastBackupAt: StateFlow<Long?> = backupSettings.lastBackupAt

    val backupRun: StateFlow<BackupRun?> = BackupWork.backupRun(context).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val restoreRun: StateFlow<BackupRun?> = BackupWork.restoreRun(context).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isDriveConnected: StateFlow<Boolean> = driveAccess.isConnected
    val driveAccount: StateFlow<String?> = driveAccess.account
    val usesMobileData: StateFlow<Boolean> = backupSettings.usesMobileData

    /** The library's folders and which are backed up to Drive - null while the library is still being read. */
    val folders: StateFlow<List<BackupFolder>?> = backupFolders.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Asks for every backup kept to be read again - after one is deleted, or Drive's could not be read. */
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The backups kept on this device, read again each time a backup run moves on. */
    private val deviceBackups = combine(backupRun, refreshes.onStart { emit(Unit) }) { _, _ -> localBackups.list() }.flowOn(Dispatchers.IO)

    /**
     * The backups kept in Drive while it is connected - read again as a backup run moves on, or when asked to. What
     * was read last stands while it is read again, so the list does not blink.
     */
    val driveBackupList: StateFlow<SettingsLoad<StoredBackup>> =
        combine(driveAccess.isConnected, backupRun, refreshes.onStart { emit(Unit) }) { isConnected, _, _ -> isConnected }
            .transformLatest { isConnected ->
                if (!isConnected) return@transformLatest emit(SettingsLoad.Empty)
                emit(
                    try {
                        settingsLoadOf(driveBackups.list())
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        SettingsLoad.Failed
                    },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsLoad.Loading)

    /** Every backup kept, the latest first - one row each, whether on this device, in Drive or both. */
    val restorableBackups: StateFlow<List<RestorableBackup>> =
        combine(deviceBackups, driveBackupList) { onDevice, inDrive ->
            val inDriveByName = (inDrive as? SettingsLoad.Loaded)?.items.orEmpty().associateBy { it.name }
            val onDeviceByName = onDevice.associateBy { it.name }
            (onDeviceByName.keys + inDriveByName.keys).map { name ->
                val kept = onDeviceByName[name] ?: inDriveByName.getValue(name)
                RestorableBackup(kept.createdAt, kept.sizeBytes, onDeviceByName[name], inDriveByName[name])
            }.sortedByDescending { it.createdAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _restoreStep = MutableStateFlow<RestoreStep?>(null)
    val restoreStep: StateFlow<RestoreStep?> = _restoreStep.asStateFlow()

    /** How the backup being restored is read, kept to read it again with a passphrase. */
    private var openBackup: (suspend () -> InputStream)? = null
    private var opening: Job? = null

    fun backUpNow() = BackupWork.backUp(context)

    /** The library's files as the folder picker offers them, with those backed up - null while they are read. */
    val filePicks: StateFlow<BackupFilePicks?> = backupFolders.filePicks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Backs up the folders and files [picked], and leaves the others out. */
    fun backUpFolders(picked: Set<String>) {
        val files = filePicks.value ?: return
        viewModelScope.launch { backupFolders.backUpOnly(picked, files) }
    }

    fun pauseBackup() {
        viewModelScope.launch { BackupWork.pauseBackup(context) }
    }

    fun resumeBackup() {
        viewModelScope.launch { BackupWork.resumeBackup(context) }
    }

    fun stopBackup() {
        viewModelScope.launch { BackupWork.stopBackup(context) }
    }

    fun setUsesMobileData(uses: Boolean) {
        viewModelScope.launch { backupSettings.setUsesMobileData(uses) }
    }

    fun setSchedule(schedule: BackupSchedule) {
        viewModelScope.launch { backupSettings.setSchedule(schedule) }
    }

    /** Encrypts backups from now on with [passphrase] - or, for null, stops - telling [onFinished] how it went. */
    fun setPassphrase(passphrase: String?, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) { repository.encryptBackups(passphrase?.toCharArray()) }
    }

    fun retryDriveBackups() {
        refreshes.tryEmit(Unit)
    }

    /** Deletes [backup] from this device and from Drive, each where asked, telling [onFinished] how it went. */
    fun delete(backup: RestorableBackup, fromDevice: Boolean, fromDrive: Boolean, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) {
            try {
                repository.delete(backup.onDevice.takeIf { fromDevice }, backup.inDrive.takeIf { fromDrive })
            } finally {
                refreshes.tryEmit(Unit)
            }
        }
    }

    /**
     * Connects Drive - at once where it was allowed before, else through Google's consent screen, which [onConsent]
     * opens and [finishConnectingDrive] hears back from. [onFailed] hears of a connection that did not happen.
     */
    fun connectDrive(onConsent: (PendingIntent) -> Unit, onFailed: () -> Unit) {
        viewModelScope.launch {
            when (val connection = runCatching { driveAccess.connect() }.getOrNull()) {
                is DriveConnection.NeedsConsent -> onConsent(connection.consent)
                DriveConnection.Connected -> Unit
                null -> onFailed()
            }
        }
    }

    fun finishConnectingDrive(data: Intent?, onFailed: () -> Unit) {
        viewModelScope.launch { if (!driveAccess.finishConnecting(data)) onFailed() }
    }

    fun disconnectDrive(onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) { driveAccess.disconnect() }
    }

    fun open(backup: RestorableBackup) {
        val (destination, kept) = backup.onDevice?.let { localBackups to it } ?: (driveBackups to backup.inDrive!!)
        openWith(read = { destination.open(kept) })
    }

    fun open(uri: Uri) {
        openWith(read = { context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString()) })
    }

    fun submitPassphrase(passphrase: String) {
        openBackup?.let { openWith(it, passphrase.toCharArray()) }
    }

    /** Restores the backup opened, bringing back the missing files whose fingerprints are [filesToRestore]. */
    fun restore(filesToRestore: Set<String>) {
        viewModelScope.launch {
            repository.chooseFilesToRestore(filesToRestore)
            BackupWork.restore(context)
            dismissRestore()
        }
    }

    fun dismissRestore() {
        opening?.cancel()
        _restoreStep.value = null
        openBackup = null
    }

    private fun openWith(read: suspend () -> InputStream, passphrase: CharArray? = null) {
        openBackup = read
        _restoreStep.value = RestoreStep.Opening
        opening = viewModelScope.launch {
            _restoreStep.value = when (val opened = repository.open(read, passphrase)) {
                is OpenedBackup.Ready -> RestoreStep.Ready(opened.preview)
                OpenedBackup.NeedsPassphrase -> RestoreStep.Passphrase(isWrong = false)
                OpenedBackup.WrongPassphrase -> RestoreStep.Passphrase(isWrong = true)
                OpenedBackup.Unsupported -> RestoreStep.Problem(R.string.restore_unsupported)
                OpenedBackup.Unreadable -> RestoreStep.Problem(R.string.restore_unreadable)
            }
        }
    }
}
