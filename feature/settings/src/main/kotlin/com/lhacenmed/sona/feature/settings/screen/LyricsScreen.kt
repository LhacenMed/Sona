package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.LyricsBackgroundStyle
import com.lhacenmed.sona.core.designsystem.component.SonaConfirmationDialog
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSliderItem
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import java.util.Locale
import kotlin.math.roundToInt

/** How lyrics read, what they are romanized into, and what is kept of them. Ported from ArchiveTune's `LyricsSettings`. */
object LyricsScreen : Screen {
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
        val lyricsBackgroundStyle by viewModel.lyricsBackgroundStyle.collectAsStateWithLifecycle()
        val showLyricsPlayerControls by viewModel.showLyricsPlayerControls.collectAsStateWithLifecycle()
        val romanizeJapanese by viewModel.romanizeJapanese.collectAsStateWithLifecycle()
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
                // Options in LyricsBackgroundStyle's order, so an option's index is the style it names.
                SettingsChoiceItem(
                    title = stringResource(R.string.lyrics_background_style_title),
                    options = listOf(
                        stringResource(R.string.lyrics_background_default),
                        stringResource(R.string.lyrics_background_follow_theme),
                        stringResource(R.string.lyrics_background_coloring),
                    ),
                    selectedIndex = lyricsBackgroundStyle.ordinal,
                    onSelect = { viewModel.setLyricsBackgroundStyle(LyricsBackgroundStyle.entries[it]) },
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
                SettingsSwitchItem(
                    title = stringResource(R.string.romanize_japanese_title),
                    summary = stringResource(R.string.romanize_japanese_summary),
                    checked = romanizeJapanese,
                    onCheckedChange = viewModel::setRomanizeJapanese,
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
                if (preloadQueueLyricsEnabled) {
                    SettingsSliderItem(
                        title = stringResource(R.string.lyrics_preload_count_title),
                        value = queueLyricsPreloadCount.toFloat(),
                        onValueChangeFinished = { viewModel.setQueueLyricsPreloadCount(it.roundToInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                        formatValue = { if (it.roundToInt() == 0) preloadOff else it.roundToInt().toString() },
                    )
                }
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
