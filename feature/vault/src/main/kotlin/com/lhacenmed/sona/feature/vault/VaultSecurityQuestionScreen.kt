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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.vault.R

/**
 * Creating the Private Folder's last step: a security question and answer to get back in with if the PIN
 * just chosen is forgotten. The folder is created open, and the flow closes on it.
 */
internal data object VaultSecurityQuestionScreen : Screen {
    override val isPrivate: Boolean get() = true

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: VaultAccessViewModel = hiltViewModel()
        val flow = viewModel.flow
        var question by remember { mutableStateOf("") }
        var answer by remember { mutableStateOf("") }
        var isCreating by remember { mutableStateOf(false) }

        VaultStepScaffold(title = stringResource(PinPurpose.CREATE.titleRes)) {
            val pin = flow.chosenPin
            if (pin == null) {
                // Restored without the PIN it creates the folder with, which only memory held.
                LaunchedEffect(Unit) { flow.finish() }
                return@VaultStepScaffold
            }
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.vault_set_question), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.vault_question_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringResource(R.string.vault_question)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text(stringResource(R.string.vault_answer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        isCreating = true
                        viewModel.create(pin, question, answer) {
                            navigator.go(PrivateFolderScreen)
                            flow.finish()
                        }
                    },
                    shapes = buttonPressShapes(),
                    enabled = !isCreating && question.isNotBlank() && answer.isNotBlank(),
                ) { Text(stringResource(PinPurpose.CREATE.titleRes)) }
            }
        }
    }
}
