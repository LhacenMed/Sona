package com.lhacenmed.sona.feature.download.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsChoiceItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsNavigationItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSection
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSectionDivider
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchContainerItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchSplitItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsTextFieldDialog
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.SubtitleFormat
import com.lhacenmed.sona.core.model.TranscriptionService
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.R

/**
 * Seal's Subtitle settings, and Sona's way of making subtitles a video has none of: whether downloads take a video's
 * subtitles, in which languages and format, the site's own captions of the speech or not, and whether they are embedded in
 * the video - then the speech transcribed by the service chosen, each service by the user's own key, turned on and off
 * apart from it.
 */
data object SubtitleSettingsScreen : Screen {
    override val titleRes: Int get() = R.string.downloads_settings_subtitles_title

    @Composable
    override fun Content() {
        val viewModel: DownloadsSettingsViewModel = hiltViewModel()
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val choices by viewModel.choices.collectAsStateWithLifecycle()
        var isEditingLanguages by remember { mutableStateOf(false) }
        var editingKey by remember { mutableStateOf<TranscriptionService?>(null) }
        val keySet = stringResource(R.string.downloads_settings_key_set)
        val keysSummary = stringResource(R.string.downloads_settings_transcription_summary)
        val unconverted = stringResource(R.string.downloads_settings_subtitles_unconverted)

        SettingsList {
            SettingsSection(stringResource(R.string.downloads_settings_general_section)) {
                SettingsSwitchContainerItem(
                    title = stringResource(R.string.downloads_settings_download_subtitles),
                    checked = choices.captions,
                    onCheckedChange = { takes -> viewModel.updateChoices { it.copy(captions = takes) } },
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.downloads_settings_subtitle_languages),
                    summary = preferences.subtitleLanguages.ifBlank { stringResource(R.string.downloads_settings_subtitle_languages_default) },
                    icon = SonaIcons.Language,
                    onClick = { isEditingLanguages = true },
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.downloads_settings_convert_subtitles),
                    options = SubtitleFormat.entries.map { format -> format.extension?.uppercase() ?: unconverted },
                    selectedIndex = preferences.subtitleFormat.ordinal,
                    onSelect = { index -> viewModel.updatePreferences { it.copy(subtitleFormat = SubtitleFormat.entries[index]) } },
                    icon = SonaIcons.Sync,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_auto_subtitles),
                    summary = stringResource(R.string.downloads_settings_auto_subtitles_summary),
                    icon = SonaIcons.ClosedCaption,
                    checked = preferences.writesAutoSubtitles,
                    onCheckedChange = { writes -> viewModel.updatePreferences { it.copy(writesAutoSubtitles = writes) } },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_embed_subtitles),
                    summary = stringResource(R.string.downloads_settings_embed_subtitles_summary),
                    icon = SonaIcons.Subtitles,
                    checked = !choices.subtitlesAsFile,
                    onCheckedChange = { embeds -> viewModel.updateChoices { it.copy(subtitlesAsFile = !embeds) } },
                )
                // Only embedded subtitles are kept or not beside the video: saved as files, they are nothing but.
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_keep_subtitle_files),
                    icon = SonaIcons.Save,
                    checked = preferences.keepsSubtitleFiles,
                    onCheckedChange = { keeps -> viewModel.updatePreferences { it.copy(keepsSubtitleFiles = keeps) } },
                    enabled = !choices.subtitlesAsFile,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.downloads_settings_transcription_title)) {
                // Tried first; the other after it, where it is on with its key given too.
                SettingsChoiceItem(
                    title = stringResource(R.string.downloads_settings_transcription_service_title),
                    options = TranscriptionService.entries.map { it.title },
                    selectedIndex = preferences.transcriptionService.ordinal,
                    onSelect = { index -> viewModel.updatePreferences { it.copy(transcriptionService = TranscriptionService.entries[index]) } },
                    icon = SonaIcons.RecordVoiceOver,
                )
                TranscriptionService.entries.forEach { service ->
                    // On with no key yet, it asks for one: there is nothing to transcribe with until it has it.
                    SettingsSwitchSplitItem(
                        title = stringResource(service.keyTitleRes),
                        summary = if (preferences.keyFor(service).isBlank()) keysSummary else keySet,
                        icon = SonaIcons.Key,
                        checked = preferences.isTranscribingWith(service),
                        onCheckedChange = { on ->
                            if (on && preferences.keyFor(service).isBlank()) editingKey = service else viewModel.updatePreferences { it.transcribingWith(service, on) }
                        },
                        onClick = { editingKey = service },
                    )
                }
            }
        }

        if (isEditingLanguages) {
            SubtitleLanguagesDialog(
                languages = preferences.subtitleLanguages,
                onSave = { languages ->
                    viewModel.updatePreferences { it.copy(subtitleLanguages = languages.trim()) }
                    isEditingLanguages = false
                },
                onDismiss = { isEditingLanguages = false },
            )
        }

        editingKey?.let { service ->
            SettingsTextFieldDialog(
                title = stringResource(service.keyTitleRes),
                icon = SonaIcons.Key,
                description = keysSummary,
                initialValue = preferences.keyFor(service),
                onConfirm = { key ->
                    // A key given is a service on.
                    viewModel.updatePreferences { it.withKey(service, key.trim()).transcribingWith(service, key.isNotBlank()) }
                    editingKey = null
                },
                onDismiss = { editingKey = null },
            )
        }
    }
}

private val TranscriptionService.keyTitleRes: Int
    get() = when (this) {
        TranscriptionService.SUPADATA -> R.string.downloads_settings_supadata_title
        TranscriptionService.GROQ -> R.string.downloads_settings_groq_title
    }
