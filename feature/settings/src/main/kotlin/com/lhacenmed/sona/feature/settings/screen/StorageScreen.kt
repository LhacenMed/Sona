package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsInfoItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNote
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider

/**
 * What the app keeps on disk beyond the music itself.
 *
 * Only caches: Sona plays files that are already on the device, so there is nothing downloaded to
 * manage - what it stores is the artwork and the lyrics it has decoded, and either can be thrown
 * away without losing anything the user put there.
 */
object StorageScreen : Screen {
    override val titleRes: Int get() = R.string.storage_title

    @Composable
    override fun Content() {
        SettingsList {
            SettingsSection(stringResource(R.string.storage_covers_section)) {
                SettingsInfoItem(
                    title = stringResource(R.string.storage_cover_cache_used_title),
                    value = stringResource(R.string.storage_placeholder_size),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.storage_cover_cache_limit_title),
                    options = listOf(
                        stringResource(R.string.storage_limit_128),
                        stringResource(R.string.storage_limit_256),
                        stringResource(R.string.storage_limit_512),
                        stringResource(R.string.storage_limit_unlimited),
                    ),
                    selectedIndex = 1,
                )
                SettingsActionItem(
                    title = stringResource(R.string.storage_clear_cover_cache_title),
                    summary = stringResource(R.string.storage_clear_cover_cache_summary),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.storage_library_section)) {
                SettingsInfoItem(
                    title = stringResource(R.string.storage_database_used_title),
                    value = stringResource(R.string.storage_placeholder_size),
                )
                SettingsActionItem(
                    title = stringResource(R.string.storage_rescan_title),
                    summary = stringResource(R.string.storage_rescan_summary),
                )
                SettingsNote(stringResource(R.string.storage_note))
            }
        }
    }
}
