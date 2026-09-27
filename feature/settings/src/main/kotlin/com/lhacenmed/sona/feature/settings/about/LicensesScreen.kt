package com.lhacenmed.sona.feature.settings.about

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsInfoItem
import com.lhacenmed.sona.feature.settings.component.SettingsLazyList
import com.lhacenmed.sona.feature.settings.component.SettingsLoad
import com.lhacenmed.sona.feature.settings.component.SettingsLoadStatus

/** Every library the app is built with, its version and its licenses. */
data object LicensesScreen : Screen {
    override val titleRes: Int get() = R.string.about_licenses

    @Composable
    override fun Content() {
        val viewModel: LicensesViewModel = hiltViewModel()
        val licenses by viewModel.licenses.collectAsStateWithLifecycle()
        val unknownLicense = stringResource(R.string.about_license_unknown)

        SettingsLazyList {
            item(key = "status") { SettingsLoadStatus(licenses, onRetry = viewModel::load) }
            (licenses as? SettingsLoad.Loaded)?.let { loaded ->
                itemsIndexed(loaded.items, key = { index, license -> "${license.name}:$index" }) { _, license ->
                    SettingsInfoItem(
                        title = license.name,
                        value = listOfNotNull(license.version, license.licenses ?: unknownLicense).joinToString(" · "),
                    )
                }
            }
        }
    }
}
