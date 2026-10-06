package com.lhacenmed.sona.feature.video.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.SonaChoiceRow

/**
 * The app's sheet picking one of [options] - the playback speed, say - the [selected] one's radio on, as every
 * set of choices in the app is shown. Picking one closes it.
 */
@Composable
internal fun <T> VideoChoiceSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    SonaBottomSheet(title = title, onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                SonaChoiceRow(
                    label = label(option),
                    selected = option == selected,
                    onClick = {
                        onSelect(option)
                        dismiss()
                    },
                )
            }
        }
    }
}
