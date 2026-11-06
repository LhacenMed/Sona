package com.lhacenmed.sona.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.component.LocalSettingsArrival
import com.lhacenmed.sona.feature.settings.screen.PlaybackScreen

/**
 * [screen] opened at its row titled [rowTitleRes] - a settings search's result, or anything else that sends the user
 * straight to one setting: it is drawn as ever, its list scrolled to that row, which ripples as it arrives - see
 * [LocalSettingsArrival]. Named by the title's resource rather than its text, so it still finds its row in whatever
 * language the app is in when it opens.
 */
data class SettingsRowScreen(val screen: Screen, @StringRes val rowTitleRes: Int) : Screen {
    override val titleRes: Int? get() = screen.titleRes

    @Composable
    override fun Content() {
        CompositionLocalProvider(LocalSettingsArrival provides stringResource(rowTitleRes)) { screen.Content() }
    }
}

/** Where quick play's source is chosen - what the library's quick play button opens to choose another. */
val QuickPlaySourceSetting: Screen = SettingsRowScreen(PlaybackScreen, R.string.quick_play_source_title)
