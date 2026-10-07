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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Creating the Private Folder: a PIN, then a security question and answer to get back in with if the PIN is
 * forgotten. Nothing to hear back from: the vault's state moves on, and whatever shows it moves on with it.
 *
 * [opensFolder]: whether the folder is left unlocked - inside it, yes; created only to move a file in, no.
 */
@Composable
fun VaultSetupFlow(opensFolder: Boolean, modifier: Modifier = Modifier) {
    val viewModel: VaultAccessViewModel = hiltViewModel()
    var chosenPin by remember { mutableStateOf<String?>(null) }

    val pin = chosenPin
    if (pin == null) {
        PinChooserFlow(onChosen = { chosenPin = it }, modifier = modifier)
        return
    }

    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Set a security question", style = MaterialTheme.typography.titleMedium)
        Text(
            "Answering it lets you set a new PIN if you forget this one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Question") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Answer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                isCreating = true
                viewModel.create(pin, question, answer, opens = opensFolder)
            },
            enabled = !isCreating && question.isNotBlank() && answer.isNotBlank(),
        ) { Text("Create Private Folder") }
    }
}
