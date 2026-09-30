package com.lhacenmed.sona.feature.settings.screen

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.lyrics.DictionaryState
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsDownload
import com.lhacenmed.sona.feature.settings.component.SettingsDownloadItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSliderItem
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import java.util.Locale
import kotlin.math.roundToInt

/** How lyrics read, what they are romanized into, and what is kept of them. Ported from ArchiveTune's `LyricsSettings`. */
data object LyricsScreen : Screen {
    override val titleRes: Int get() = R.string.lyrics_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: LyricsSettingsViewModel = hiltViewModel()
        val lyricsClick by viewModel.lyricsClick.collectAsStateWithLifecycle()
        val lyricsScroll by viewModel.lyricsScroll.collectAsStateWithLifecycle()
        val lyricsLineBlur by viewModel.lyricsLineBlur.collectAsStateWithLifecycle()
        val lyricsTextSize by viewModel.lyricsTextSize.collectAsStateWithLifecycle()
        val lyricsLineSpacing by viewModel.lyricsLineSpacing.collectAsStateWithLifecycle()
        val showLyricsPlayerControls by viewModel.showLyricsPlayerControls.collectAsStateWithLifecycle()
        val japaneseDictionary by viewModel.japaneseDictionaryState.collectAsStateWithLifecycle()
        val romanizeKorean by viewModel.romanizeKorean.collectAsStateWithLifecycle()
        val romanizeChinese by viewModel.romanizeChinese.collectAsStateWithLifecycle()
        val romanizeHindi by viewModel.romanizeHindi.collectAsStateWithLifecycle()
        val romanizeOtherLanguages by viewModel.romanizeOtherLanguages.collectAsStateWithLifecycle()
        val preloadQueueLyricsEnabled by viewModel.preloadQueueLyricsEnabled.collectAsStateWithLifecycle()
        val queueLyricsPreloadCount by viewModel.queueLyricsPreloadCount.collectAsStateWithLifecycle()
        var showClearLyricsDialog by rememberSaveable { mutableStateOf(false) }

        val spFormat = stringResource(R.string.sp_format)
        val lineSpacingFormat = stringResource(R.string.lyrics_line_spacing_format)
        val preloadOff = stringResource(R.string.lyrics_preload_count_off)

        if (showClearLyricsDialog) {
            SonaConfirmationDialog(
                title = stringResource(R.string.lyrics_clear_cache_title),
                message = stringResource(R.string.lyrics_clear_cache_confirm),
                confirmLabel = stringResource(R.string.lyrics_clear_cache_action),
                successMessage = stringResource(R.string.lyrics_clear_cache_done),
                failureMessage = stringResource(R.string.lyrics_clear_cache_failed),
                onDismiss = { showClearLyricsDialog = false },
                operation = viewModel::clearLyricsCache,
            )
        }

        SettingsList {
            SettingsSection(stringResource(R.string.lyrics_display_section)) {
                SettingsNavigationItem(
                    title = stringResource(R.string.lyrics_animation_style_title),
                    summary = stringResource(R.string.lyrics_animation_style_summary),
                    onClick = { navigator.go(LyricsAnimationScreen) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_click_change_title),
                    summary = stringResource(R.string.lyrics_click_change_summary),
                    checked = lyricsClick,
                    onCheckedChange = viewModel::setLyricsClick,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_auto_scroll_title),
                    summary = stringResource(R.string.lyrics_auto_scroll_summary),
                    checked = lyricsScroll,
                    onCheckedChange = viewModel::setLyricsScroll,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_blur_title),
                    summary = stringResource(R.string.lyrics_blur_summary),
                    checked = lyricsLineBlur,
                    onCheckedChange = viewModel::setLyricsLineBlur,
                )
                SettingsSliderItem(
                    title = stringResource(R.string.lyrics_text_size_title),
                    value = lyricsTextSize,
                    onValueChangeFinished = viewModel::setLyricsTextSize,
                    valueRange = 16f..36f,
                    steps = 19,
                    formatValue = { spFormat.format(it.roundToInt()) },
                )
                SettingsSliderItem(
                    title = stringResource(R.string.lyrics_line_spacing_title),
                    value = lyricsLineSpacing,
                    onValueChangeFinished = viewModel::setLyricsLineSpacing,
                    valueRange = 1.0f..2.0f,
                    steps = 19,
                    formatValue = { String.format(Locale.getDefault(), lineSpacingFormat, it) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_show_player_controls_title),
                    summary = stringResource(R.string.lyrics_show_player_controls_summary),
                    checked = showLyricsPlayerControls,
                    onCheckedChange = viewModel::setShowLyricsPlayerControls,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.lyrics_romanization_section)) {
                // Japanese alone needs data of its own - a 13 MB dictionary - so it is downloaded rather than
                // shipped, and romanizing it is on exactly while it is here.
                SettingsDownloadItem(
                    title = stringResource(R.string.romanize_japanese_title),
                    summary = japaneseDictionarySummary(japaneseDictionary),
                    download = japaneseDictionary.asSettingsDownload(),
                    onDownload = viewModel::downloadJapaneseDictionary,
                    onCancel = viewModel::cancelJapaneseDictionary,
                    onRemove = viewModel::removeJapaneseDictionary,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.romanize_korean_title),
                    summary = stringResource(R.string.romanize_korean_summary),
                    checked = romanizeKorean,
                    onCheckedChange = viewModel::setRomanizeKorean,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.romanize_chinese_title),
                    summary = stringResource(R.string.romanize_chinese_summary),
                    checked = romanizeChinese,
                    onCheckedChange = viewModel::setRomanizeChinese,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.romanize_hindi_title),
                    summary = stringResource(R.string.romanize_hindi_summary),
                    checked = romanizeHindi,
                    onCheckedChange = viewModel::setRomanizeHindi,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.romanize_other_languages_title),
                    summary = stringResource(R.string.romanize_other_languages_summary),
                    checked = romanizeOtherLanguages,
                    onCheckedChange = viewModel::setRomanizeOtherLanguages,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.lyrics_queue_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_preload_title),
                    summary = stringResource(R.string.lyrics_preload_summary),
                    checked = preloadQueueLyricsEnabled,
                    onCheckedChange = viewModel::setPreloadQueueLyricsEnabled,
                )
                SettingsSliderItem(
                    title = stringResource(R.string.lyrics_preload_count_title),
                    value = queueLyricsPreloadCount.toFloat(),
                    onValueChangeFinished = { viewModel.setQueueLyricsPreloadCount(it.roundToInt()) },
                    valueRange = 0f..10f,
                    steps = 9,
                    formatValue = { if (it.roundToInt() == 0) preloadOff else it.roundToInt().toString() },
                    enabled = preloadQueueLyricsEnabled,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.lyrics_cache_section)) {
                SettingsActionItem(
                    title = stringResource(R.string.lyrics_clear_cache_title),
                    summary = stringResource(R.string.lyrics_clear_cache_summary),
                    onClick = { showClearLyricsDialog = true },
                )
            }
        }
    }
}

@Composable
private fun japaneseDictionarySummary(state: DictionaryState): String {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    return when (state) {
        DictionaryState.Missing -> stringResource(R.string.romanize_japanese_download_summary)
        is DictionaryState.Downloading ->
            stringResource(R.string.romanize_japanese_downloading_summary, size(state.receivedBytes), size(state.totalBytes))
        DictionaryState.Installed -> stringResource(R.string.romanize_japanese_summary)
        DictionaryState.Failed -> stringResource(R.string.romanize_japanese_failed_summary)
    }
}

private fun DictionaryState.asSettingsDownload(): SettingsDownload = when (this) {
    DictionaryState.Missing -> SettingsDownload.Available
    is DictionaryState.Downloading -> SettingsDownload.InProgress(progress)
    DictionaryState.Installed -> SettingsDownload.Done
    DictionaryState.Failed -> SettingsDownload.Failed
}
