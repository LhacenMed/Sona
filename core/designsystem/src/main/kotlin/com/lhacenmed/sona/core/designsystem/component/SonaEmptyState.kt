package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes

/** A button an empty screen offers, to fill it - the playlists' "Create playlist". */
class EmptyStateAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * What a screen or a list shows while it has nothing to: a [title], a [message] saying why or what to do, and -
 * where something can be done about it there and then - an [action]. Centred both ways, its text too, so a message
 * that runs to several lines stays centred under its title. The one empty state every screen shows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SonaEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: EmptyStateAction? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Button(
                onClick = action.onClick,
                shapes = buttonPressShapes(),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Icon(action.icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(action.label)
            }
        }
    }
}
