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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.MIN_SECRET_CODE_LENGTH
import com.lhacenmed.sona.core.vault.VaultState
import com.lhacenmed.sona.feature.vault.R

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

        VaultStepScaffold(title = stringResource(R.string.vault_secret_code), needsOpenVault = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    text = stringResource(R.string.vault_secret_code_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(if (hasSecretCode) R.string.vault_new_secret_code else R.string.vault_secret_code)) },
                    supportingText = { Text(stringResource(R.string.vault_secret_code_length, MIN_SECRET_CODE_LENGTH)) },
                    isError = code.isNotEmpty() && !isLongEnough,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text(stringResource(R.string.vault_confirm_secret_code)) },
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
                ) { Text(stringResource(R.string.vault_save)) }
                if (hasSecretCode) {
                    TextButton(
                        onClick = { isRemoving = true },
                        shapes = buttonPressShapes(),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text(stringResource(R.string.vault_remove_secret_code)) }
                }
            }
        }

        if (isRemoving) {
            SonaConfirmationDialog(
                title = stringResource(R.string.vault_remove_secret_code),
                message = stringResource(R.string.vault_remove_secret_code_message),
                confirmLabel = stringResource(R.string.vault_remove),
                successMessage = stringResource(R.string.vault_secret_code_removed),
                failureMessage = stringResource(R.string.vault_secret_code_remove_failed),
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
