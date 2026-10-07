package com.lhacenmed.sona.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

/** A PIN's digits as dots - filled for what has been typed, hollow for what's left. */
@Composable
internal fun PinDots(length: Int, filled: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(length) { index ->
            androidx.compose.foundation.layout.Box(
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

/** The 0-9 grid every PIN entry/choice screen types on. */
@Composable
internal fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { digit -> KeypadButton(label = digit.toString(), enabled = enabled, onClick = { onDigit(digit) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.size(72.dp))
            KeypadButton(label = "0", enabled = enabled, onClick = { onDigit('0') })
            IconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
            }
        }
    }
}

@Composable
private fun KeypadButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(72.dp)) {
        Text(text = label, style = MaterialTheme.typography.headlineMedium)
    }
}

private enum class PinChooserStep { CHOOSE, CONFIRM }

/**
 * Picks a 4-8 digit PIN and confirms it, then hands the result to [onChosen] - the one flow vault
 * creation, "forgot PIN" recovery, and changing an existing PIN all share.
 */
@Composable
internal fun PinChooserFlow(onChosen: (String) -> Unit, modifier: Modifier = Modifier) {
    var step by remember { mutableStateOf(PinChooserStep.CHOOSE) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (step) {
            PinChooserStep.CHOOSE -> {
                Text("Choose a PIN (4-8 digits)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp))
                PinDots(length = maxOf(pin.length, 4), filled = pin.length)
                Spacer(Modifier.height(24.dp))
                NumericKeypad(onDigit = { if (pin.length < 8) pin += it }, onBackspace = { pin = pin.dropLast(1) })
                Spacer(Modifier.height(16.dp))
                Button(onClick = { step = PinChooserStep.CONFIRM }, enabled = pin.length in 4..8) { Text("Next") }
            }

            PinChooserStep.CONFIRM -> {
                Text("Confirm your PIN", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp))
                PinDots(length = pin.length, filled = confirmPin.length.coerceAtMost(pin.length))
                if (mismatch) {
                    Spacer(Modifier.height(8.dp))
                    Text("PINs don't match", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(24.dp))
                NumericKeypad(
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
}
