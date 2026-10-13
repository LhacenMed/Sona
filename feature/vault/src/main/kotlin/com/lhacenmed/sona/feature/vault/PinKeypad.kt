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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes

/** How many digits a PIN can have. */
internal val PinLengths = 4..8

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
