package com.lhacenmed.sona.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.Screen

/** How the app looks: its theme, and the colours it draws itself in. */
object LookAndFeelScreen : Screen {
    override val titleRes: Int get() = R.string.look_and_feel_title

    @Composable
    override fun Content() {
        SettingsList {
            SettingsChoiceItem(
                title = stringResource(R.string.theme_title),
                value = stringResource(R.string.theme_value_automatic),
                icon = Icons.Filled.Contrast,
            )
            SettingsChoiceItem(
                title = stringResource(R.string.color_scheme_title),
                value = stringResource(R.string.color_scheme_value_dynamic),
                icon = Icons.Filled.Palette,
            )
            SettingsSwitchItem(
                title = stringResource(R.string.black_theme_title),
                summary = stringResource(R.string.black_theme_summary),
            )
            SettingsSwitchItem(
                title = stringResource(R.string.round_mode_title),
                summary = stringResource(R.string.round_mode_summary),
                initialValue = true,
            )
            SettingsSwitchItem(
                title = stringResource(R.string.cover_colors_title),
                summary = stringResource(R.string.cover_colors_summary),
            )
        }
    }
}
