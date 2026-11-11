package com.lhacenmed.sona.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.R as DesignR
import com.lhacenmed.sona.core.designsystem.component.SonaSettingsRow
import com.lhacenmed.sona.core.designsystem.component.SonaSettingsValue
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.section.ColumnSection
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.VaultState
import com.lhacenmed.sona.feature.vault.R

/**
 * How the Private Folder is found and opened - WhatsApp's chat lock settings, for it: hidden from Settings, its secret
 * code the way to it then; opened by fingerprint or face beside its PIN; and its PIN changed. Reached from inside the
 * folder only, so nothing outside it says it is there.
 */
internal data object PrivateFolderSettingsScreen : Screen {
    override val isPrivate: Boolean get() = true

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: VaultAccessViewModel = hiltViewModel()
        val vault by viewModel.state.collectAsStateWithLifecycle()

        VaultStepScaffold(title = stringResource(R.string.vault_settings_title), needsOpenVault = true) {
            val current = vault as? VaultState.Created ?: return@VaultStepScaffold
            // Turned on only once the fingerprint or face is given, so it is known to work before it is relied on.
            val enableBiometrics = rememberBiometricCheck(title = stringResource(R.string.vault_biometrics_title), cancelLabel = stringResource(DesignR.string.confirmation_dialog_cancel)) {
                viewModel.setUnlocksWithBiometrics(true)
            }
            // Hidden, the secret code is the one way left in: with none yet, hiding starts by choosing it, and saving it
            // hides the folder.
            val setHidden: (Boolean) -> Unit = { hidden ->
                if (hidden && !current.hasSecretCode) navigator.go(VaultSecretCodeScreen(hidesFolder = true)) else viewModel.setHidden(hidden)
            }
            val setUnlocksWithBiometrics: (Boolean) -> Unit = { enabled ->
                if (enabled) enableBiometrics?.invoke() else viewModel.setUnlocksWithBiometrics(false)
            }
            val canUseBiometrics = enableBiometrics != null || current.unlocksWithBiometrics

            val scrollState = rememberScrollState()
            FastScroller(scrollState, Modifier.fillMaxSize()) { overscrollEffect ->
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState, overscrollEffect = overscrollEffect)) {
                    ColumnSection(title = stringResource(R.string.vault_privacy)) {
                        SonaSettingsRow(
                            title = stringResource(R.string.vault_hide_title),
                            summary = stringResource(R.string.vault_hide_summary),
                            onClick = { setHidden(!current.isHidden) },
                            trailing = { Switch(checked = current.isHidden, onCheckedChange = setHidden) },
                        )
                        SonaSettingsRow(
                            title = stringResource(R.string.vault_secret_code),
                            summary = stringResource(R.string.vault_secret_code_summary),
                            onClick = { navigator.go(VaultSecretCodeScreen()) },
                            trailing = { SonaSettingsValue(stringResource(if (current.hasSecretCode) R.string.vault_on else R.string.vault_off)) },
                        )
                    }
                    ColumnSection(title = stringResource(R.string.vault_unlocking)) {
                        SonaSettingsRow(
                            title = stringResource(R.string.vault_biometrics_title),
                            summary = if (canUseBiometrics) {
                                stringResource(R.string.vault_biometrics_summary)
                            } else {
                                stringResource(R.string.vault_biometrics_unavailable)
                            },
                            enabled = canUseBiometrics,
                            onClick = { setUnlocksWithBiometrics(!current.unlocksWithBiometrics) },
                            trailing = {
                                Switch(checked = current.unlocksWithBiometrics, onCheckedChange = setUnlocksWithBiometrics, enabled = canUseBiometrics)
                            },
                        )
                        SonaSettingsRow(
                            title = stringResource(PinPurpose.CHANGE.titleRes),
                            summary = stringResource(R.string.vault_change_pin_summary),
                            onClick = { navigator.go(VaultChangePinScreen) },
                        )
                    }
                }
            }
        }
    }
}
