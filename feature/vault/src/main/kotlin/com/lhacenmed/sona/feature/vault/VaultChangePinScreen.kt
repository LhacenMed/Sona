package com.lhacenmed.sona.feature.vault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.VaultState

/** Changing the Private Folder's PIN, from inside it: the current PIN again, before a new one is chosen. */
internal data object VaultChangePinScreen : Screen {
    override val isPrivate: Boolean get() = true

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: VaultAccessViewModel = hiltViewModel()
        val vault by viewModel.state.collectAsStateWithLifecycle()

        VaultStepScaffold(title = PinPurpose.CHANGE.title, needsOpenVault = true) {
            val current = vault as? VaultState.Created ?: return@VaultStepScaffold
            PinEntry(
                title = "Enter your current PIN",
                vault = current,
                onGranted = { navigator.go(VaultChoosePinScreen(PinPurpose.CHANGE)) },
            )
        }
    }
}
