package com.lhacenmed.sona.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.MIN_SECRET_CODE_LENGTH
import com.lhacenmed.sona.core.vault.VaultState

/**
 * Choosing the Private Folder's secret code - typed twice, as a PIN is chosen - or, with one set, removing it: the
 * folder then shows in Settings again, as a hidden one has nothing else to be found by.
 *
 * [hidesFolder]: opened on the way to hiding the folder, which a secret code is needed for - saving the code hides it
 * too, so the one step does what was asked.
 */
internal data class VaultSecretCodeScreen(val hidesFolder: Boolean = false) : Screen {
    override val isPrivate: Boolean get() = true

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: VaultAccessViewModel = hiltViewModel()
        val vault by viewModel.state.collectAsStateWithLifecycle()
        var code by remember { mutableStateOf("") }
        var confirmation by remember { mutableStateOf("") }
        var isRemoving by remember { mutableStateOf(false) }
        val hasSecretCode = (vault as? VaultState.Created)?.hasSecretCode == true
        val isLongEnough = code.trim().length >= MIN_SECRET_CODE_LENGTH
        val isConfirmed = isLongEnough && confirmation.trim().equals(code.trim(), ignoreCase = true)

        VaultStepScaffold(title = "Secret code", needsOpenVault = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    text = "Type it into the library's search to open the Private Folder - a word, an emoji, anything easy " +
                        "for you to remember and hard for others to guess. Case doesn't count.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(if (hasSecretCode) "New secret code" else "Secret code") },
                    supportingText = { Text("At least $MIN_SECRET_CODE_LENGTH characters") },
                    isError = code.isNotEmpty() && !isLongEnough,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text("Confirm secret code") },
                    isError = confirmation.isNotEmpty() && !isConfirmed,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.setSecretCode(code, hidesFolder, onSaved = navigator::back) },
                    enabled = isConfirmed,
                    shapes = buttonPressShapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
                if (hasSecretCode) {
                    TextButton(
                        onClick = { isRemoving = true },
                        shapes = buttonPressShapes(),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text("Remove secret code") }
                }
            }
        }

        if (isRemoving) {
            SonaConfirmationDialog(
                title = "Remove secret code",
                message = "The Private Folder will show in Settings again.",
                confirmLabel = "Remove",
                successMessage = "Secret code removed",
                failureMessage = "Could not remove the secret code",
                onDismiss = { isRemoving = false },
                operation = { onFinished ->
                    viewModel.setSecretCode(null, hidesFolder = false) {
                        onFinished(true)
                        navigator.back()
                    }
                },
            )
        }
    }
}
