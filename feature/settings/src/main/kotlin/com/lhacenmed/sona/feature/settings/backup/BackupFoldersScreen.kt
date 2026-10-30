package com.lhacenmed.sona.feature.settings.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.backup.BackupFolder
import com.lhacenmed.sona.feature.backup.BackupFolders
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsLoad
import com.lhacenmed.sona.feature.settings.component.SettingsLoadStatus
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import com.lhacenmed.sona.feature.settings.component.settingsLoadOf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The library's music folders, then its video folders, each switched on or off for backing up to Google Drive. */
data object BackupFoldersScreen : Screen {
    override val titleRes: Int get() = R.string.backup_folders_title

    @Composable
    override fun Content() {
        val viewModel: BackupFoldersViewModel = hiltViewModel()
        val load by viewModel.folders.collectAsStateWithLifecycle()
        val folders = (load as? SettingsLoad.Loaded)?.items.orEmpty()
        val (videoFolders, musicFolders) = folders.partition { it.isVideo }

        SettingsList {
            SettingsLoadStatus(load, onRetry = {})
            if (musicFolders.isNotEmpty()) {
                SettingsSection(stringResource(R.string.backup_folders_music)) {
                    musicFolders.forEach { folder ->
                        FolderItem(folder, pluralStringResource(R.plurals.backup_folder_songs, folder.fileCount, folder.fileCount), viewModel::setBackedUp)
                    }
                }
            }
            if (musicFolders.isNotEmpty() && videoFolders.isNotEmpty()) SettingsSectionDivider()
            if (videoFolders.isNotEmpty()) {
                SettingsSection(stringResource(R.string.backup_folders_videos)) {
                    videoFolders.forEach { folder ->
                        FolderItem(folder, pluralStringResource(R.plurals.backup_folder_videos, folder.fileCount, folder.fileCount), viewModel::setBackedUp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderItem(folder: BackupFolder, fileCount: String, onBackedUpChange: (BackupFolder, Boolean) -> Unit) {
    SettingsSwitchItem(
        title = folder.name,
        summary = fileCount,
        checked = folder.isBackedUp,
        onCheckedChange = { onBackedUpChange(folder, it) },
    )
}

@HiltViewModel
class BackupFoldersViewModel @Inject constructor(
    private val backupFolders: BackupFolders,
) : ViewModel() {

    val folders: StateFlow<SettingsLoad<BackupFolder>> = backupFolders.folders
        .map { folders -> folders?.let(::settingsLoadOf) ?: SettingsLoad.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsLoad.Loading)

    fun setBackedUp(folder: BackupFolder, isBackedUp: Boolean) {
        viewModelScope.launch { backupFolders.setBackedUp(folder, isBackedUp) }
    }
}
