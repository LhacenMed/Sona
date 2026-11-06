package com.lhacenmed.sona.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaSettingsRow
import com.lhacenmed.sona.core.designsystem.component.SonaSettingsValue
import com.lhacenmed.sona.core.designsystem.component.section.ColumnSection
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.VaultState

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

        VaultStepScaffold(title = "Private Folder settings", needsOpenVault = true) {
            val current = vault as? VaultState.Created ?: return@VaultStepScaffold
            // Turned on only once the fingerprint or face is given, so it is known to work before it is relied on.
            val enableBiometrics = rememberBiometricCheck(title = "Unlock with biometrics", cancelLabel = "Cancel") {
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

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ColumnSection(title = "Privacy") {
                    SonaSettingsRow(
                        title = "Hide Private Folder",
                        summary = "It won't show in Settings. To open it, type your secret code into the library's search.",
                        onClick = { setHidden(!current.isHidden) },
                        trailing = { Switch(checked = current.isHidden, onCheckedChange = setHidden) },
                    )
                    SonaSettingsRow(
                        title = "Secret code",
                        summary = "Open the Private Folder by typing a code of your own into the library's search.",
                        onClick = { navigator.go(VaultSecretCodeScreen()) },
                        trailing = { SonaSettingsValue(if (current.hasSecretCode) "On" else "Off") },
                    )
                }
                ColumnSection(title = "Unlocking") {
                    SonaSettingsRow(
                        title = "Unlock with biometrics",
                        summary = if (canUseBiometrics) {
                            "Open it with your fingerprint or face, your PIN still there for when they don't work."
                        } else {
                            "No fingerprint or face is set up on this device."
                        },
                        enabled = canUseBiometrics,
                        onClick = { setUnlocksWithBiometrics(!current.unlocksWithBiometrics) },
                        trailing = {
                            Switch(checked = current.unlocksWithBiometrics, onCheckedChange = setUnlocksWithBiometrics, enabled = canUseBiometrics)
                        },
                    )
                    SonaSettingsRow(
                        title = "Change PIN",
                        summary = "Choose a new PIN, after the one you have now.",
                        onClick = { navigator.go(VaultChangePinScreen) },
                    )
                }
            }
        }
    }
}
