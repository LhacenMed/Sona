package com.lhacenmed.sona.feature.download.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsActionItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSection
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSectionDivider
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchSplitItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsTextFieldDialog
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.R

/** The fastest rate Seal's dialog takes, in kilobytes a second. */
private const val MaxRateKilobytes = 1_000_000

/** What a dialog that edits a setting is open on. */
private enum class NetworkDialog { RATE_LIMIT, PROXY, FRAGMENTS }

/** Seal's Network settings: how fast and over which networks downloads run, and how they reach their sites. */
data object NetworkSettingsScreen : Screen {
    override val titleRes: Int get() = R.string.downloads_settings_network_title

    @Composable
    override fun Content() {
        val viewModel: DownloadsSettingsViewModel = hiltViewModel()
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        var dialog by remember { mutableStateOf<NetworkDialog?>(null) }

        SettingsList {
            SettingsSection(stringResource(R.string.downloads_settings_general_section)) {
                SettingsSwitchSplitItem(
                    title = stringResource(R.string.downloads_settings_rate_title),
                    summary = stringResource(R.string.downloads_settings_rate_summary),
                    icon = SonaIcons.Speed,
                    checked = preferences.limitsRate,
                    onCheckedChange = { limits -> viewModel.updatePreferences { it.copy(limitsRate = limits) } },
                    onClick = { dialog = NetworkDialog.RATE_LIMIT },
                )
                // Seal's way round: downloading over a metered network is what is turned on, where Sona keeps waiting for Wi-Fi.
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_cellular_title),
                    summary = stringResource(R.string.downloads_settings_cellular_summary),
                    icon = if (preferences.wifiOnly) SonaIcons.SignalCellularOff else SonaIcons.SignalCellular,
                    checked = !preferences.wifiOnly,
                    onCheckedChange = { cellular -> viewModel.updatePreferences { it.copy(wifiOnly = !cellular) } },
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.downloads_settings_advanced_section)) {
                SettingsSwitchSplitItem(
                    title = stringResource(R.string.downloads_settings_proxy_title),
                    summary = stringResource(R.string.downloads_settings_proxy_summary),
                    icon = SonaIcons.VpnKey,
                    checked = preferences.usesProxy,
                    onCheckedChange = { uses -> viewModel.updatePreferences { it.copy(usesProxy = uses) } },
                    onClick = { dialog = NetworkDialog.PROXY },
                )
                SettingsActionItem(
                    title = stringResource(R.string.downloads_settings_fragments_title),
                    summary = stringResource(R.string.downloads_settings_fragments_summary),
                    icon = SonaIcons.OfflineBolt,
                    onClick = { dialog = NetworkDialog.FRAGMENTS },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_ipv4_title),
                    summary = stringResource(R.string.downloads_settings_ipv4_summary),
                    icon = SonaIcons.SettingsEthernet,
                    checked = preferences.forcesIpv4,
                    onCheckedChange = { forces -> viewModel.updatePreferences { it.copy(forcesIpv4 = forces) } },
                )
            }
        }

        val dismiss = { dialog = null }
        when (dialog) {
            NetworkDialog.RATE_LIMIT -> SettingsTextFieldDialog(
                title = stringResource(R.string.downloads_settings_rate_title),
                icon = SonaIcons.Speed,
                description = stringResource(R.string.downloads_settings_rate_summary),
                label = stringResource(R.string.downloads_settings_max_rate),
                suffix = stringResource(R.string.downloads_settings_rate_unit),
                keyboardType = KeyboardType.Number,
                initialValue = kilobytesOf(preferences.rateLimit),
                isValid = { it.toIntOrNull() in 1..MaxRateKilobytes },
                onConfirm = { rate ->
                    viewModel.updatePreferences { it.copy(rateLimit = "${rate}K") }
                    dismiss()
                },
                onDismiss = dismiss,
            )
            NetworkDialog.PROXY -> SettingsTextFieldDialog(
                title = stringResource(R.string.downloads_settings_proxy_title),
                icon = SonaIcons.VpnKey,
                description = stringResource(R.string.downloads_settings_proxy_summary),
                label = stringResource(R.string.downloads_settings_proxy_title),
                keyboardType = KeyboardType.Uri,
                initialValue = preferences.proxy,
                onConfirm = { proxy ->
                    viewModel.updatePreferences { it.copy(proxy = proxy.trim()) }
                    dismiss()
                },
                onDismiss = dismiss,
            )
            NetworkDialog.FRAGMENTS -> ConcurrentFragmentsDialog(
                count = preferences.concurrentFragments,
                onSave = { count ->
                    viewModel.updatePreferences { it.copy(concurrentFragments = count) }
                    dismiss()
                },
                onDismiss = dismiss,
            )
            null -> Unit
        }
    }
}

/** [rate] - `2M`, `500K`, `1048576` - in whole kilobytes, as Seal's dialog edits it: empty where it is no rate. */
private fun kilobytesOf(rate: String): String {
    val match = Regex("""(\d+(?:\.\d+)?)\s*([KkMmGg]?)""").matchEntire(rate.trim()) ?: return ""
    val number = match.groupValues[1].toDouble()
    val kilobytes = when (match.groupValues[2].lowercase()) {
        "k" -> number
        "m" -> number * 1024
        "g" -> number * 1024 * 1024
        else -> number / 1024
    }
    return kilobytes.toLong().takeIf { it > 0 }?.toString().orEmpty()
}
