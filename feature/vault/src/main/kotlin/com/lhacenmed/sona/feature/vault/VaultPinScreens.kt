package com.lhacenmed.sona.feature.vault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.VaultAccessResult
import com.lhacenmed.sona.feature.vault.R

/**
 * Choosing a 4-8 digit PIN, for [purpose] - the one step vault creation, "forgot PIN" recovery and changing
 * the PIN all share. Confirming it is the next screen's.
 */
internal data class VaultChoosePinScreen(val purpose: PinPurpose) : Screen {
    override val isPrivate: Boolean get() = true

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: VaultAccessViewModel = hiltViewModel()
        var pin by remember { mutableStateOf("") }

        VaultStepScaffold(title = stringResource(purpose.titleRes), needsOpenVault = purpose == PinPurpose.CHANGE) {
            PinPad(
                title = stringResource(R.string.vault_choose_pin, PinLengths.first, PinLengths.last),
                dotCount = maxOf(pin.length, PinLengths.first),
                filledDots = pin.length,
                message = null,
                onDigit = { if (pin.length < PinLengths.last) pin += it },
                onBackspace = { pin = pin.dropLast(1) },
                onDone = {
                    viewModel.flow.chosenPin = pin
                    navigator.go(VaultConfirmPinScreen(purpose))
                },
                isDoneEnabled = pin.length in PinLengths,
            )
        }
    }
}

/**
 * The PIN just chosen typed again, then put to [purpose]: a new folder goes on to its security question, a
 * forgotten PIN is replaced and the folder opens, a changed one is saved - and the flow closes on the folder.
 */
internal data class VaultConfirmPinScreen(val purpose: PinPurpose) : Screen {
    override val isPrivate: Boolean get() = true

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel: VaultAccessViewModel = hiltViewModel()
        val flow = viewModel.flow
        var confirmPin by remember { mutableStateOf("") }
        var mismatch by remember { mutableStateOf(false) }

        VaultStepScaffold(title = stringResource(purpose.titleRes), needsOpenVault = purpose == PinPurpose.CHANGE) {
            val pin = flow.chosenPin
            if (pin == null) {
                // Restored without the PIN it confirms, which only memory held.
                LaunchedEffect(Unit) { flow.finish() }
                return@VaultStepScaffold
            }
            val onConfirmed = {
                when (purpose) {
                    PinPurpose.CREATE -> navigator.go(VaultSecurityQuestionScreen)
                    PinPurpose.RESET -> viewModel.resetPin(flow.verifiedAnswer.orEmpty(), pin) { result ->
                        if (result == VaultAccessResult.Granted) navigator.go(PrivateFolderScreen)
                        flow.finish()
                    }
                    PinPurpose.CHANGE -> viewModel.changePin(pin) {
                        context.toast(context.getString(R.string.vault_pin_changed))
                        flow.finish()
                    }
                }
            }
            PinPad(
                title = stringResource(R.string.vault_confirm_pin),
                dotCount = pin.length,
                filledDots = confirmPin.length,
                message = if (mismatch) stringResource(R.string.vault_pins_differ) else null,
                onDigit = { digit ->
                    if (confirmPin.length < pin.length) {
                        confirmPin += digit
                        if (confirmPin.length == pin.length) {
                            mismatch = confirmPin != pin
                            confirmPin = ""
                            if (!mismatch) onConfirmed()
                        }
                    }
                },
                onBackspace = { confirmPin = confirmPin.dropLast(1) },
            )
        }
    }
}
