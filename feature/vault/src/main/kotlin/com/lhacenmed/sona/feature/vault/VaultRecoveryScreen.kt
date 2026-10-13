package com.lhacenmed.sona.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.VaultAccessResult
import com.lhacenmed.sona.core.vault.VaultState

/**
 * A forgotten PIN's first step: the security question answered, before a new PIN is chosen - the folder
 * opens only once that is set, so it is never left open with a PIN nobody remembers.
 */
internal data object VaultRecoveryScreen : Screen {
    override val isPrivate: Boolean get() = true

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: VaultAccessViewModel = hiltViewModel()
        val vault by viewModel.state.collectAsStateWithLifecycle()
        var answer by remember { mutableStateOf("") }
        var isChecking by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        VaultStepScaffold(title = PinPurpose.RESET.title) {
            val current = vault as? VaultState.Created ?: return@VaultStepScaffold
            val secondsLockedOut = rememberSecondsUntil(current.lockedUntilEpochMs)
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current.securityQuestion, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Answer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (secondsLockedOut > 0) "Too many tries - wait ${secondsLockedOut}s" else error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                SonaActionButtonGroup {
                    actionButton(label = "Back to PIN", onClick = navigator::back)
                    actionButton(
                        label = "Continue",
                        onClick = {
                            isChecking = true
                            viewModel.verifySecurityAnswer(answer) { result ->
                                isChecking = false
                                error = if (result == VaultAccessResult.WrongSecret) "That's not the answer" else null
                                if (result == VaultAccessResult.Granted) {
                                    viewModel.flow.verifiedAnswer = answer
                                    navigator.go(VaultChoosePinScreen(PinPurpose.RESET))
                                }
                            }
                        },
                        enabled = answer.isNotBlank() && !isChecking && secondsLockedOut == 0L,
                    )
                }
            }
        }
    }
}
