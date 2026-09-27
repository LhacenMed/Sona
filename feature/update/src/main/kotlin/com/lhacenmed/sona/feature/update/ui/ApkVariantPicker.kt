@file:OptIn(ExperimentalMaterial3Api::class)

package com.lhacenmed.sona.feature.update.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.feature.update.R
import com.lhacenmed.sona.feature.update.github.ReleaseApk

/**
 * Which of a release's [apks] to update with - the [selected] one, and the rest a tap away: each with its
 * download size, the device's own processor's and the universal one marked recommended, and those this device
 * cannot run listed but not offered.
 */
@Composable
internal fun ApkVariantPicker(
    apks: List<ReleaseApk>,
    selected: ReleaseApk,
    onSelect: (ReleaseApk) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { isExpanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.variant.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.update_variant)) },
            suffix = { Text(Formatter.formatShortFileSize(context, selected.sizeBytes)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            apks.forEach { apk ->
                val variant = apk.variant
                DropdownMenuItem(
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(variant.label)
                            if (variant.isRecommended) RecommendedBadge()
                        }
                    },
                    trailingIcon = {
                        Text(
                            text = if (variant.isSupported) {
                                Formatter.formatShortFileSize(context, apk.sizeBytes)
                            } else {
                                stringResource(R.string.update_variant_unsupported)
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    enabled = variant.isSupported,
                    onClick = {
                        onSelect(apk)
                        isExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RecommendedBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.update_variant_recommended),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
