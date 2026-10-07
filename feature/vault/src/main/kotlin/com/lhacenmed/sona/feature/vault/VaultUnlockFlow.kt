package com.lhacenmed.sona.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.vault.VaultAccessResult
import com.lhacenmed.sona.core.vault.VaultState
import kotlinx.coroutines.delay

/** Opening the locked Private Folder: its PIN, or - forgotten - its security question and a new PIN. */
@Composable
internal fun VaultUnlockFlow(vault: VaultState.Created, modifier: Modifier = Modifier) {
    var isRecovering by remember { mutableStateOf(false) }
    if (isRecovering) {
        VaultRecoveryFlow(vault = vault, onCancel = { isRecovering = false }, modifier = modifier)
        return
    }
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PinEntry(title = "Enter your PIN", vault = vault, onGranted = {})
        TextButton(onClick = { isRecovering = true }) { Text("Forgot PIN?") }
    }
}

/**
 * The vault's PIN typed in, checked the moment its last digit is - the vault is unlocked as it matches, and
 * [onGranted] hears it. Wrong guesses past the free few lock the keypad, counting down to the next try.
 */
@Composable
internal fun PinEntry(title: String, vault: VaultState.Created, onGranted: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: VaultAccessViewModel = hiltViewModel()
    var pin by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val secondsLockedOut = rememberSecondsUntil(vault.lockedUntilEpochMs)

    Column(modifier = modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        PinDots(length = vault.pinLength, filled = pin.length)
        Spacer(Modifier.height(8.dp))
        // Always laid out, so the keypad never moves as a message comes and goes.
        Text(
            text = if (secondsLockedOut > 0) "Too many tries - wait ${secondsLockedOut}s" else error.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        NumericKeypad(
            enabled = secondsLockedOut == 0L && !isChecking,
            onDigit = { digit ->
                pin += digit
                if (pin.length == vault.pinLength) {
                    isChecking = true
                    viewModel.unlock(pin) { result ->
                        isChecking = false
                        pin = ""
                        error = if (result == VaultAccessResult.WrongSecret) "Wrong PIN" else null
                        if (result == VaultAccessResult.Granted) onGranted()
                    }
                }
            },
            onBackspace = { pin = pin.dropLast(1) },
        )
    }
}

/** Whole seconds left until [epochMs], ticking down to 0 - 0 straight away for a time already past. */
@Composable
internal fun rememberSecondsUntil(epochMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(epochMs) {
        now = System.currentTimeMillis()
        while (now < epochMs) {
            // Wakes as the shown second turns over, not on a fixed tick that drifts from it.
            delay((epochMs - now - 1) % 1_000L + 1)
            now = System.currentTimeMillis()
        }
    }
    return ((epochMs - now + 999) / 1_000).coerceAtLeast(0)
}
