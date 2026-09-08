package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.model.Track
import java.util.Locale

/**
 * Shared building blocks for the library's list/detail screens, kept small and private-ish to
 * this module so every screen (Tracks excluded - it predates this file) feels like a sibling.
 */

/**
 * The shell every library list screen renders inside: it keeps the screen's root layout shape
 * identical across all three states, so nothing reflows as data arrives.
 *
 * A null [items] means the database has not been read yet and renders nothing - showing an "empty
 * library" message during that window would claim something untrue, which is what made the app
 * flash a scanning/permission message on launch even with a full library already cached.
 */
@Composable
internal fun <T> LibraryListContent(
    items: List<T>?,
    hasPermission: Boolean,
    isScanning: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable (List<T>) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            items == null -> Unit
            items.isEmpty() -> {
                val (title, message) = emptyLibraryStateContent(
                    hasPermission = hasPermission,
                    isScanning = isScanning,
                    emptyTitle = emptyTitle,
                    emptyMessage = emptyMessage,
                )
                EmptyLibraryState(title = title, message = message)
            }
            else -> content(items)
        }
    }
}

/**
 * Resolves what an empty list screen should say. A list is empty for one of three genuinely
 * different reasons - conflating them (as a plain "no items" message would) is what caused Sona to
 * flash a "grant permission" message even when permission was already granted: the real reason was
 * simply that the scan hadn't populated the database yet.
 */
internal fun emptyLibraryStateContent(
    hasPermission: Boolean,
    isScanning: Boolean,
    emptyTitle: String,
    emptyMessage: String,
): Pair<String, String> = when {
    !hasPermission -> "Permission needed" to "Sona needs access to your audio files to show your library."
    isScanning -> "Scanning your library…" to "This only takes a moment."
    else -> emptyTitle to emptyMessage
}

@Composable
internal fun EmptyLibraryState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
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
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Header used by the entity detail screens, which render full-bleed and draw their own back bar. */
@Composable
internal fun DetailHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
internal fun DetailTrackRow(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isPlaying) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "${track.artist} · ${formatTrackDuration(track.durationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
