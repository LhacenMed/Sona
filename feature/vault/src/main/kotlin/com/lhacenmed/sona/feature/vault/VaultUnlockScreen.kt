package com.lhacenmed.sona.feature.vault

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.VaultAccessResult
import com.lhacenmed.sona.core.vault.VaultState
import kotlinx.coroutines.delay

/** Opening the locked Private Folder: its PIN, or - forgotten - its security question and a new PIN. */
internal data object VaultUnlockScreen : Screen {
    override val isPrivate: Boolean get() = true

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: VaultAccessViewModel = hiltViewModel()
        val vault by viewModel.state.collectAsStateWithLifecycle()

        VaultStepScaffold(title = "Private Folder") {
            val current = vault as? VaultState.Created ?: return@VaultStepScaffold
            PinEntry(
                title = "Enter your PIN",
                vault = current,
                onGranted = {
                    navigator.go(PrivateFolderScreen)
                    viewModel.flow.finish()
                },
            )
            TextButton(
                onClick = { navigator.go(VaultRecoveryScreen) },
                shapes = buttonPressShapes(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Forgot PIN?") }
        }
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
    val lockoutMessage = rememberLockoutMessage(vault.lockedUntilEpochMs)

    PinPad(
        title = title,
        dotCount = vault.pinLength,
        filledDots = pin.length,
        message = lockoutMessage ?: error,
        modifier = modifier,
        // Only a lockout greys the keys: the check itself takes a moment, which input is ignored for instead,
        // so the keypad never flickers as each PIN is checked.
        enabled = lockoutMessage == null,
        onDigit = { digit ->
            if (!isChecking) {
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
            }
        },
        onBackspace = { if (!isChecking) pin = pin.dropLast(1) },
    )
}

/**
 * What a guess is refused with until [lockedUntilEpochMs], counting down each second - null once guesses
 * are taken again. Every PIN and answer step shows the vault's one lockout through this.
 */
@Composable
internal fun rememberLockoutMessage(lockedUntilEpochMs: Long): String? {
    val seconds = rememberSecondsUntil(lockedUntilEpochMs)
    return if (seconds > 0) "Too many tries - wait ${seconds}s" else null
}

/** Whole seconds left until [epochMs], ticking down to 0 - 0 straight away for a time already past. */
@Composable
private fun rememberSecondsUntil(epochMs: Long): Long {
    // Read afresh for each new time, in the same frame it arrives: a clock kept from before it would put
    // the time seconds ahead for that frame, and show a lockout that is not there.
    var now by remember(epochMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(epochMs) {
        while (now < epochMs) {
            // Wakes as the shown second turns over, not on a fixed tick that drifts from it.
            delay((epochMs - now - 1) % 1_000L + 1)
            now = System.currentTimeMillis()
        }
    }
    return ((epochMs - now + 999) / 1_000).coerceAtLeast(0)
}
