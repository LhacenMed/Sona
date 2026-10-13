package com.lhacenmed.sona.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes

/** How many digits a PIN can have. */
private val PinLengths = 4..8

/** Every key of the keypad, and the gap between them. */
private val KeySize = 72.dp
private val KeySpacing = 16.dp

/**
 * Every PIN step's one layout - its [title], the PIN's dots, a line for [message], then the keypad - so the
 * keypad sits in the same place on every step, whatever each says. [onDone], where a step has one, is the
 * keypad's own key, enabled while [isDoneEnabled].
 */
@Composable
internal fun PinPad(
    title: String,
    dotCount: Int,
    filledDots: Int,
    message: String?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
    isDoneEnabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        PinDots(length = dotCount, filled = filledDots)
        Spacer(Modifier.height(8.dp))
        // Always laid out, so the keypad never moves as a message comes and goes.
        Text(text = message.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        NumericKeypad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            enabled = enabled,
            onDone = onDone,
            isDoneEnabled = isDoneEnabled,
        )
    }
}

/** A PIN's digits as dots - filled for what has been typed, hollow for what's left. */
@Composable
private fun PinDots(length: Int, filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(length) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (index < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * The 0-9 grid, Backspace and - given [onDone] - Done, as the system's own PIN pad lays them out. Each key is
 * a button of its own, pressing alone: none gives way to its neighbours, so no key moves under a finger.
 */
@Composable
private fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean,
    onDone: (() -> Unit)?,
    isDoneEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KeySpacing), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
                row.forEach { digit -> DigitKey(digit = digit, enabled = enabled, onClick = { onDigit(digit) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
            SonaIconButton(
                onClick = onBackspace,
                icon = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                modifier = Modifier.size(KeySize),
                enabled = enabled,
            )
            DigitKey(digit = '0', enabled = enabled, onClick = { onDigit('0') })
            if (onDone == null) {
                Spacer(Modifier.size(KeySize))
            } else {
                SonaIconButton(
                    onClick = onDone,
                    icon = Icons.Filled.Check,
                    contentDescription = "Done",
                    modifier = Modifier.size(KeySize),
                    enabled = enabled && isDoneEnabled,
                    colors = IconButtonDefaults.filledIconButtonColors(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shapes = buttonPressShapes(),
        modifier = Modifier.size(KeySize),
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(text = digit.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}

private enum class PinChooserStep { CHOOSE, CONFIRM }

/**
 * Picks a 4-8 digit PIN and confirms it, then hands the result to [onChosen] - the one flow vault
 * creation, "forgot PIN" recovery, and changing an existing PIN all share. Back from confirming goes back
 * to the PIN as it was typed.
 */
@Composable
internal fun PinChooserFlow(onChosen: (String) -> Unit, modifier: Modifier = Modifier) {
    var step by remember { mutableStateOf(PinChooserStep.CHOOSE) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    VaultSteps(
        step = step,
        modifier = modifier,
        onBack = if (step == PinChooserStep.CONFIRM) {
            {
                step = PinChooserStep.CHOOSE
                confirmPin = ""
                mismatch = false
            }
        } else {
            null
        },
    ) { shownStep ->
        when (shownStep) {
            PinChooserStep.CHOOSE -> PinPad(
                title = "Choose a PIN (${PinLengths.first}-${PinLengths.last} digits)",
                dotCount = maxOf(pin.length, PinLengths.first),
                filledDots = pin.length,
                message = null,
                onDigit = { if (pin.length < PinLengths.last) pin += it },
                onBackspace = { pin = pin.dropLast(1) },
                onDone = { step = PinChooserStep.CONFIRM },
                isDoneEnabled = pin.length in PinLengths,
            )

            PinChooserStep.CONFIRM -> PinPad(
                title = "Confirm your PIN",
                dotCount = pin.length,
                filledDots = confirmPin.length.coerceAtMost(pin.length),
                message = if (mismatch) "PINs don't match" else null,
                onDigit = { digit ->
                    if (confirmPin.length < pin.length) {
                        confirmPin += digit
                        if (confirmPin.length == pin.length) {
                            if (confirmPin == pin) {
                                onChosen(pin)
                            } else {
                                mismatch = true
                                confirmPin = ""
                            }
                        }
                    }
                },
                onBackspace = { confirmPin = confirmPin.dropLast(1) },
            )
        }
    }
}
