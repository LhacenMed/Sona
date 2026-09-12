package com.lhacenmed.sona.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

/**
 * Settings home: the four things a setting can be about.
 *
 * Categories rather than one long list, because the list is long enough that finding anything in it
 * would mean reading all of it. Each category owns a screen, so a setting added later lands in the
 * one place it belongs instead of on the end.
 */
object SettingsScreen : Screen {
    override val titleRes: Int get() = R.string.settings_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        SettingsList {
            SettingsNavigationItem(
                title = stringResource(R.string.look_and_feel_title),
                summary = stringResource(R.string.look_and_feel_summary),
                icon = Icons.Filled.Palette,
                onClick = { navigator.go(LookAndFeelScreen) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.personalize_title),
                summary = stringResource(R.string.personalize_summary),
                icon = Icons.Filled.Tune,
                onClick = { navigator.go(PersonalizeScreen) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.content_title),
                summary = stringResource(R.string.content_summary),
                icon = Icons.Filled.MusicNote,
                onClick = { navigator.go(ContentScreen) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.audio_title),
                summary = stringResource(R.string.audio_summary),
                icon = Icons.Filled.PlayArrow,
                onClick = { navigator.go(AudioScreen) },
            )
        }
    }
}
