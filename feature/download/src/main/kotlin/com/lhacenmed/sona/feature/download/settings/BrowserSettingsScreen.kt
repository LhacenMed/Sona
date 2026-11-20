package com.lhacenmed.sona.feature.download.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsChoiceItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsNavigationItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSection
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchItem
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.AdBlockMode
import com.lhacenmed.sona.core.model.CookieBlockMode
import com.lhacenmed.sona.core.model.FingerprintMode
import com.lhacenmed.sona.core.model.HttpsUpgradeMode
import com.lhacenmed.sona.core.model.SearchEngine
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.browser.BrowserViewModel

/** The browser's own settings: what it searches with, and its Shields and privacy - Brave's, every default its. */
data object BrowserSettingsScreen : Screen {
    override val titleRes: Int get() = R.string.downloads_settings_browser_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: BrowserViewModel = hiltViewModel()
        val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle()
        val shields by viewModel.shieldsPreferences.collectAsStateWithLifecycle()
        val update = viewModel.shields::update
        val disabled = stringResource(R.string.browser_shields_disabled)
        val standard = stringResource(R.string.browser_shields_standard)
        SettingsList {
            SettingsChoiceItem(
                title = stringResource(R.string.browser_settings_search_engine),
                options = SearchEngine.entries.map(SearchEngine::title),
                selectedIndex = searchEngine.ordinal,
                onSelect = { viewModel.onSearchEngineChange(SearchEngine.entries[it]) },
                icon = SonaIcons.Search,
            )
            SettingsSection(title = stringResource(R.string.browser_shields_and_privacy)) {
                // Each choice's options in its mode's order - strongest first, as Brave lists them.
                SettingsChoiceItem(
                    title = stringResource(R.string.browser_shields_ad_block),
                    options = listOf(stringResource(R.string.browser_shields_aggressive), standard, disabled),
                    selectedIndex = shields.adBlock.ordinal,
                    defaultIndex = AdBlockMode.STANDARD.ordinal,
                    onSelect = { index -> update { it.copy(adBlock = AdBlockMode.entries[index]) } },
                    icon = SonaIcons.Shield,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.browser_shields_https_upgrade),
                    options = listOf(stringResource(R.string.browser_shields_strict), standard, disabled),
                    selectedIndex = shields.httpsUpgrade.ordinal,
                    defaultIndex = HttpsUpgradeMode.STANDARD.ordinal,
                    onSelect = { index -> update { it.copy(httpsUpgrade = HttpsUpgradeMode.entries[index]) } },
                    summary = stringResource(R.string.browser_shields_https_upgrade_summary),
                    icon = SonaIcons.Lock,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.browser_shields_block_scripts),
                    summary = stringResource(R.string.browser_shields_block_scripts_summary),
                    checked = shields.blockScripts,
                    onCheckedChange = { checked -> update { it.copy(blockScripts = checked) } },
                    icon = SonaIcons.Code,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.browser_shields_fingerprinting),
                    options = listOf(stringResource(R.string.browser_shields_strict_may_break), standard, disabled),
                    selectedIndex = shields.fingerprinting.ordinal,
                    defaultIndex = FingerprintMode.STANDARD.ordinal,
                    onSelect = { index -> update { it.copy(fingerprinting = FingerprintMode.entries[index]) } },
                    summary = stringResource(R.string.browser_shields_fingerprinting_summary),
                    icon = SonaIcons.Fingerprint,
                )
                // Every cookie setting in the one place - the browser's own and its sign-ins downloads read.
                SettingsNavigationItem(
                    title = stringResource(R.string.downloads_settings_cookies_title),
                    summary = stringResource(R.string.downloads_settings_cookies_summary),
                    icon = SonaIcons.Cookie,
                    onClick = { navigator.go(CookieProfilesScreen) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.browser_shields_forget),
                    summary = stringResource(R.string.browser_shields_forget_summary),
                    checked = shields.forgetOnClose,
                    onCheckedChange = { checked -> update { it.copy(forgetOnClose = checked) } },
                    icon = SonaIcons.AutoDelete,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.browser_shields_language),
                    summary = stringResource(R.string.browser_shields_language_summary),
                    checked = shields.reduceLanguage,
                    onCheckedChange = { checked -> update { it.copy(reduceLanguage = checked) } },
                    icon = SonaIcons.Language,
                    enabled = shields.fingerprinting != FingerprintMode.OFF,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.browser_shields_de_amp),
                    summary = stringResource(R.string.browser_shields_de_amp_summary),
                    checked = shields.deAmp,
                    onCheckedChange = { checked -> update { it.copy(deAmp = checked) } },
                    icon = SonaIcons.Bolt,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.browser_shields_debounce),
                    summary = stringResource(R.string.browser_shields_debounce_summary),
                    checked = shields.debounce,
                    onCheckedChange = { checked -> update { it.copy(debounce = checked) } },
                    icon = SonaIcons.LinkOff,
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.browser_shields_content_filtering),
                    summary = stringResource(R.string.browser_shields_content_filtering_summary),
                    onClick = { navigator.go(ContentFilteringScreen) },
                    icon = SonaIcons.FilterList,
                    enabled = shields.adBlock != AdBlockMode.OFF,
                )
            }
        }
    }
}
