package com.lhacenmed.sona.feature.settings.backup

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.feature.backup.BackupRepository
import com.lhacenmed.sona.feature.backup.BackupSchedule
import com.lhacenmed.sona.feature.backup.BackupSettings
import com.lhacenmed.sona.feature.backup.LocalBackup
import com.lhacenmed.sona.feature.backup.LocalBackups
import com.lhacenmed.sona.feature.backup.OpenedBackup
import com.lhacenmed.sona.feature.backup.RestorePreview
import com.lhacenmed.sona.feature.backup.work.BackupRun
import com.lhacenmed.sona.feature.backup.work.BackupWork
import com.lhacenmed.sona.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where opening a backup to restore it stands, as its dialog shows it. */
sealed interface RestoreStep {
    data object Opening : RestoreStep

    /** The backup is encrypted with a passphrase this device does not hold - [isWrong] after one that did not open it. */
    data class Passphrase(val isWrong: Boolean) : RestoreStep

    data class Ready(val preview: RestorePreview) : RestoreStep

    data class Problem(@StringRes val messageRes: Int) : RestoreStep
}

/** The [BackupScreen]'s state: how backups are made, the ones kept here, and a backup being restored. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BackupRepository,
    private val backupSettings: BackupSettings,
    private val localBackups: LocalBackups,
) : ViewModel() {

    val schedule: StateFlow<BackupSchedule> = backupSettings.schedule
    val isEncrypted: StateFlow<Boolean> = backupSettings.isEncrypted
    val lastBackupAt: StateFlow<Long?> = backupSettings.lastBackupAt

    val backupRun: StateFlow<BackupRun?> = BackupWork.backupRun(context).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val restoreRun: StateFlow<BackupRun?> = BackupWork.restoreRun(context).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The backups kept on this device, read again each time a backup run moves on. */
    val backups: StateFlow<List<LocalBackup>> = backupRun
        .map { localBackups.list() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Where backups are kept, as a path a file manager finds. */
    val location: String = localBackups.directory.path

    private val _restoreStep = MutableStateFlow<RestoreStep?>(null)
    val restoreStep: StateFlow<RestoreStep?> = _restoreStep.asStateFlow()

    /** How the backup being restored is read, kept to read it again with a passphrase. */
    private var openBackup: (() -> InputStream)? = null
    private var opening: Job? = null

    fun backUpNow() = BackupWork.backUp(context)

    fun setSchedule(schedule: BackupSchedule) {
        viewModelScope.launch { backupSettings.setSchedule(schedule) }
    }

    /** Encrypts backups from now on with [passphrase] - or, for null, stops - telling [onFinished] how it went. */
    fun setPassphrase(passphrase: String?, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) { repository.encryptBackups(passphrase?.toCharArray()) }
    }

    fun open(backup: LocalBackup) {
        openWith(read = { backup.file.inputStream() })
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

    private fun openWith(read: () -> InputStream, passphrase: CharArray? = null) {
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
