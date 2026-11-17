package com.lhacenmed.sona.feature.download.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsNavigationItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSection
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSectionDivider
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.rememberDownloadFolderPicker

/**
 * Seal's Download directory: the folder each kind of download is saved in - and what downloads with a command, where one is
 * set - and the template their files are named by.
 */
data object DownloadDirectorySettingsScreen : Screen {
    override val titleRes: Int get() = R.string.downloads_settings_directory_title

    @Composable
    override fun Content() {
        val viewModel: DownloadsSettingsViewModel = hiltViewModel()
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        var isEditingTemplate by remember { mutableStateOf(false) }
        var isEditingCommandFolder by remember { mutableStateOf(false) }

        SettingsList {
            SettingsSection(stringResource(R.string.downloads_settings_general_section)) {
                listOf(
                    Triple(DownloadKind.VIDEO, R.string.downloads_settings_video_folder_title, SonaIcons.VideoLibrary),
                    Triple(DownloadKind.AUDIO, R.string.downloads_settings_audio_folder_title, SonaIcons.LibraryMusic),
                    Triple(DownloadKind.FILE, R.string.downloads_settings_file_folder_title, SonaIcons.Folder),
                ).forEach { (kind, title, icon) ->
                    val pickFolder = rememberDownloadFolderPicker { folder -> viewModel.updatePreferences { it.withFolder(kind, folder) } }
                    SettingsNavigationItem(title = stringResource(title), summary = preferences.folderFor(kind), icon = icon, onClick = pickFolder)
                }
                SettingsNavigationItem(
                    title = stringResource(R.string.downloads_settings_command_folder_title),
                    summary = preferences.commandFolder.ifBlank { stringResource(R.string.downloads_settings_command_folder_unset) },
                    icon = SonaIcons.Folder,
                    onClick = { isEditingCommandFolder = true },
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.downloads_settings_advanced_section)) {
                SettingsNavigationItem(
                    title = stringResource(R.string.downloads_settings_output_template_title),
                    summary = stringResource(R.string.downloads_settings_output_template_summary),
                    icon = SonaIcons.FolderSpecial,
                    onClick = { isEditingTemplate = true },
                )
            }
        }

        if (isEditingTemplate) {
            OutputTemplateDialog(
                template = preferences.fileNameTemplate,
                onSave = { template ->
                    viewModel.updatePreferences { it.copy(fileNameTemplate = template) }
                    isEditingTemplate = false
                },
                onDismiss = { isEditingTemplate = false },
            )
        }

        if (isEditingCommandFolder) {
            CommandFolderDialog(
                folder = preferences.commandFolder,
                onSave = { folder ->
                    viewModel.updatePreferences { it.copy(commandFolder = folder) }
                    isEditingCommandFolder = false
                },
                onDismiss = { isEditingCommandFolder = false },
            )
        }
    }
}
