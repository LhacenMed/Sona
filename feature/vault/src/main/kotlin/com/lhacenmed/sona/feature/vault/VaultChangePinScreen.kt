package com.lhacenmed.sona.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.VaultState

/** Changing the Private Folder's PIN, from inside it: the current PIN again, then the new one. */
data object VaultChangePinScreen : Screen {
    override val isPrivate: Boolean get() = true

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel: VaultAccessViewModel = hiltViewModel()
        val vault by viewModel.state.collectAsStateWithLifecycle()
        var isCurrentPinGiven by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(title = "Change PIN", onNavigateBack = navigator::back)
            val current = vault as? VaultState.Created ?: return@Column
            // Locked while away, the current PIN is asked for again before a new one is taken.
            if (isCurrentPinGiven && current.isUnlocked) {
                PinChooserFlow(
                    onChosen = { newPin ->
                        viewModel.changePin(newPin) {
                            context.toast("PIN changed")
                            navigator.back()
                        }
                    },
                )
            } else {
                PinEntry(title = "Enter your current PIN", vault = current, onGranted = { isCurrentPinGiven = true })
            }
        }
    }
}
