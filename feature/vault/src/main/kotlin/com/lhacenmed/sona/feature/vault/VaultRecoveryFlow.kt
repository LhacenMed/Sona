package com.lhacenmed.sona.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.vault.VaultAccessResult
import com.lhacenmed.sona.core.vault.VaultState

/**
 * A forgotten PIN: the security question answered, then a new PIN chosen - the folder opens only once it
 * is set, so it is never left open with a PIN nobody remembers.
 */
@Composable
internal fun VaultRecoveryFlow(vault: VaultState.Created, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: VaultAccessViewModel = hiltViewModel()
    var answer by remember { mutableStateOf("") }
    var verifiedAnswer by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val secondsLockedOut = rememberSecondsUntil(vault.lockedUntilEpochMs)

    val verified = verifiedAnswer
    if (verified != null) {
        PinChooserFlow(onChosen = { newPin -> viewModel.resetPin(verified, newPin) }, modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(vault.securityQuestion, style = MaterialTheme.typography.titleMedium)
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
        Button(
            onClick = {
                isChecking = true
                viewModel.verifySecurityAnswer(answer) { result ->
                    isChecking = false
                    error = if (result == VaultAccessResult.WrongSecret) "That's not the answer" else null
                    if (result == VaultAccessResult.Granted) verifiedAnswer = answer
                }
            },
            enabled = answer.isNotBlank() && !isChecking && secondsLockedOut == 0L,
        ) { Text("Continue") }
        TextButton(onClick = onCancel) { Text("Back to PIN") }
    }
}
