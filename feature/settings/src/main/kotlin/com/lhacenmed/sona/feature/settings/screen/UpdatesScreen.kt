package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsInfoItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import com.lhacenmed.sona.feature.update.installedVersionName
import com.lhacenmed.sona.feature.update.ui.UpdateGate

/**
 * Whether a found update announces itself, the way to ask when it does not, and the version running.
 *
 * Two rows for one decision: with the alerts on there is nothing left to ask for, so Check now is only
 * live while they are off. Its answer, when there is one, is the update dialog - opened here, over
 * this screen, rather than on the library behind it.
 */
object UpdatesScreen : Screen {
    override val titleRes: Int get() = R.string.updates_title

    @Composable
    override fun Content() {
        val viewModel: UpdatesSettingsViewModel = hiltViewModel()
        val autoPrompt by viewModel.autoPrompt.collectAsStateWithLifecycle()
        val checkStatus by viewModel.checkStatus.collectAsStateWithLifecycle()

        SettingsList {
            SettingsSection(stringResource(R.string.updates_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.updates_automatic_check_title),
                    summary = stringResource(R.string.updates_automatic_check_summary),
                    checked = autoPrompt,
                    onCheckedChange = viewModel::setAutoPrompt,
                )
                SettingsActionItem(
                    title = stringResource(R.string.updates_check_now_title),
                    summary = when (checkStatus) {
                        UpdateCheckStatus.IDLE -> stringResource(R.string.updates_check_now_summary)
                        UpdateCheckStatus.CHECKING -> stringResource(R.string.updates_checking)
                        UpdateCheckStatus.UP_TO_DATE -> stringResource(R.string.updates_up_to_date)
                    },
                    onClick = viewModel::checkForUpdate,
                    enabled = !autoPrompt && checkStatus != UpdateCheckStatus.CHECKING,
                )
                SettingsInfoItem(
                    title = stringResource(R.string.updates_installed_version_title),
                    value = LocalContext.current.installedVersionName(),
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.changelog_title),
                    summary = stringResource(R.string.changelog_summary),
                    onClick = {},
                )
            }
        }

        // Only ever asked here: the alerts, when on, are the library's to show.
        UpdateGate(autoPrompt = false)
    }
}
