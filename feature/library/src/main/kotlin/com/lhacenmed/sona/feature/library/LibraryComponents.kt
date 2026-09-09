package com.lhacenmed.sona.feature.library

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.model.Track
import java.util.Locale

/**
 * Shared building blocks for the library's list/detail screens, kept small and private-ish to
 * this module so every screen feels like a sibling.
 */

/** How many placeholder rows a loading list draws. Enough to fill a phone screen, no more. */
private const val PLACEHOLDER_ROW_COUNT = 12

/**
 * The shell every library list screen renders inside. It keeps the screen's root layout shape
 * identical across all three states, so nothing reflows as data arrives.
 *
 * [LibraryContent.Loading] draws a placeholder list rather than either an empty-library message
 * (which would be a lie) or blank space (which reads as a broken screen, and was the "empty for a
 * second, then everything appears at once" the library used to show on launch). Because the
 * placeholder rows are the same height as real ones, the real list replaces them in place instead of
 * pushing the screen around.
 */
@Composable
internal fun <T> LibraryListContent(
    content: LibraryContent<T>,
    hasPermission: Boolean,
    isScanning: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    body: @Composable (List<T>) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            content is LibraryContent.Loading -> LoadingListPlaceholder()
            content is LibraryContent.Ready && content.items.isEmpty() -> {
                val (title, message) = emptyLibraryStateContent(
                    hasPermission = hasPermission,
                    isScanning = isScanning,
                    emptyTitle = emptyTitle,
                    emptyMessage = emptyMessage,
                )
                EmptyLibraryState(title = title, message = message)
            }
            content is LibraryContent.Ready -> body(content.items)
        }
    }
}

/**
 * [LibraryListContent] plus the `LazyColumn` every list screen was writing out by hand.
 *
 * Centralising it is what makes stable [key]s and a [contentType] non-optional. Without a key, Lazy
 * layouts fall back to item *position*, so when the library changes every row is treated as a
 * different row: scroll position jumps, and nothing can be reused. With one, a rescan that reorders
 * or inserts a few tracks moves the existing rows instead of rebuilding the list.
 */
@Composable
internal fun <T> LibraryList(
    content: LibraryContent<T>,
    hasPermission: Boolean,
    isScanning: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    LibraryListContent(
        content = content,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        modifier = modifier,
    ) { items ->
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = items,
                key = key,
                // Every row in these lists is the same composable shape, so telling Compose that
                // lets it reuse a scrolled-off row's slot table wholesale instead of rebuilding it.
                contentType = { LIST_ROW_CONTENT_TYPE },
            ) { item -> row(item) }
        }
    }
}

private const val LIST_ROW_CONTENT_TYPE = "libraryRow"

/** The two-line row shape shared by the artists, albums, genres and folders tabs. */
@Composable
internal fun LibraryEntityRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A quietly pulsing set of row-shaped blocks.
 *
 * Deliberately low-contrast and slow: the loading window should normally be a frame or two (the
 * library is already in memory by then), so this must never read as a "loading spinner" moment. It
 * only becomes visible at all on a genuinely slow first read.
 */
@Composable
private fun LoadingListPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "libraryPlaceholder")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "libraryPlaceholderAlpha",
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant

    // A plain Column, not a LazyColumn: the count is fixed and small, and a lazy container here
    // would allocate scroll state that is thrown away as soon as the real list arrives.
    Column(modifier = modifier.fillMaxSize()) {
        repeat(PLACEHOLDER_ROW_COUNT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlaceholderBar(widthFraction = 0.55f, alpha = alpha, color = color)
                PlaceholderBar(widthFraction = 0.32f, alpha = alpha * 0.7f, color = color)
            }
        }
    }
}

@Composable
private fun PlaceholderBar(
    widthFraction: Float,
    alpha: Float,
    color: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(12.dp)
            .clip(RoundedCornerShape(4.dp))
            // drawBehind, not background(): the colour changes every animation frame, and drawing
            // it in the draw phase skips recomposition entirely.
            .drawBehind { drawRect(color = color.copy(alpha = alpha)) },
    )
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

/**
 * The whole body of a detail screen: a header, then that entity's tracks.
 *
 * The album, artist, genre and folder detail screens differ only in their heading and their query,
 * so they share this. It also means they inherit the list screens' loading state - previously they
 * started from a non-null empty state and so briefly rendered "no tracks" over a list that existed.
 */
@Composable
internal fun TrackListDetail(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    viewModel: TrackListDetailViewModel,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        DetailHeader(title = title, subtitle = subtitle, onBack = onBack)
        LibraryList(
            content = tracks,
            // A detail screen is only reachable from a library that already loaded, so neither the
            // permission nor the scanning explanation can apply here.
            hasPermission = true,
            isScanning = false,
            emptyTitle = "No tracks found",
            emptyMessage = emptyMessage,
            key = { it.id },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { track ->
            TrackRow(
                track = track,
                isPlaying = { track.id == currentTrackId },
                onClick = { viewModel.onTrackClick(track) },
            )
        }
    }
}

/**
 * A track row.
 *
 * [isPlaying] is a lambda, not a value, on purpose. Passed as a `Boolean`, every row in the list
 * recomposes whenever the playing track changes, because each row's parameters changed. Passed as a
 * lambda read inside the row's own composition, only the row that was highlighted and the row that
 * now is do any work.
 */
@Composable
internal fun TrackRow(
    track: Track,
    isPlaying: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = isPlaying()
    val background = if (playing) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
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
