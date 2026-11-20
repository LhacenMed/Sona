package com.lhacenmed.sona.feature.download.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsActionItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsNote
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchItem
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.browser.BrowserViewModel

/**
 * Brave's content filtering: the filter lists of its catalog one can choose - regional lists, cookie notices', annoyances' -
 * each on or off; those making up the Shields themselves are not among them.
 */
data object ContentFilteringScreen : Screen {
    override val titleRes: Int get() = R.string.browser_shields_content_filtering

    @Composable
    override fun Content() {
        val viewModel: BrowserViewModel = hiltViewModel()
        val shields by viewModel.shieldsPreferences.collectAsStateWithLifecycle()
        val lists by viewModel.filterLists.collectAsStateWithLifecycle()
        val isUpdating by viewModel.shields.lists.isUpdating.collectAsStateWithLifecycle()
        SettingsList {
            SettingsActionItem(
                title = stringResource(if (isUpdating) R.string.browser_shields_updating_lists else R.string.browser_shields_update_lists),
                onClick = viewModel.shields.lists::update,
                icon = SonaIcons.Refresh,
                isBusy = isUpdating,
            )
            val choosable = lists.filterNot { it.isHidden }
            if (choosable.isEmpty()) SettingsNote(stringResource(R.string.browser_shields_no_lists))
            choosable.forEach { list ->
                SettingsSwitchItem(
                    title = list.title,
                    summary = list.description.takeIf(String::isNotEmpty),
                    checked = list.isOn(shields),
                    onCheckedChange = { on ->
                        viewModel.shields.update {
                            // Kept only where it differs from the catalog's own default, which may change.
                            it.copy(
                                listsOn = if (on && !list.isDefault) it.listsOn + list.id else it.listsOn - list.id,
                                listsOff = if (!on && list.isDefault) it.listsOff + list.id else it.listsOff - list.id,
                            )
                        }
                    },
                )
            }
        }
    }
}
