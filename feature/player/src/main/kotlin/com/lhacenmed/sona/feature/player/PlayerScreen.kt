package com.lhacenmed.sona.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

private val CollapsedHeight = 72.dp

/**
 * The expandable player: a draggable sheet that goes from [CollapsedHeight] (the mini bar) to
 * fullscreen, driven by an expansion fraction in `0f..1f` that cross-fades the collapsed
 * [MiniPlayerBar] content out and the full player content in - the Compose-idiomatic equivalent
 * of Fossify's `BackportBottomSheetBehavior` slide-offset-driven alpha cross-fade (see the
 * research doc's "state machine driving cross-sheet transitions" section). `Modifier.draggable`
 * with a hand-rolled anchor/fling (rather than `anchoredDraggable`) is used so this doesn't
 * depend on a specific Compose Foundation minor version's anchoredDraggable signature.
 */
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    onGoToAlbum: (Long) -> Unit = {},
    onGoToArtist: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val track = uiState.currentTrack ?: return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val expansion = remember { Animatable(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val dragRangePx = remember(maxHeight, density) {
            with(density) { (maxHeight - CollapsedHeight).toPx() }.coerceAtLeast(1f)
        }

        val draggableState = rememberDraggableState { delta ->
            val target = (expansion.value - delta / dragRangePx).coerceIn(0f, 1f)
            scope.launch { expansion.snapTo(target) }
        }

        // Dark scrim behind the sheet, fading in as it approaches fullscreen - mirrors Fossify's
        // `main_sheet_scrim` view, driven by the same fraction rather than a separate listener.
        if (expansion.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f * expansion.value)),
            )
        }

        val sheetHeight = lerp(CollapsedHeight, maxHeight, expansion.value)

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = draggableState,
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity < -1000f -> 1f
                            velocity > 1000f -> 0f
                            else -> if (expansion.value > 0.5f) 1f else 0f
                        }
                        scope.launch { expansion.animateTo(target, tween(250)) }
                    },
                ),
            tonalElevation = 3.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (expansion.value < 1f) {
                    MiniPlayerBar(
                        track = track,
                        isPlaying = uiState.playback.isPlaying,
                        onTogglePlayPause = viewModel::onTogglePlayPause,
                        onExpand = { scope.launch { expansion.animateTo(1f, tween(250)) } },
                        modifier = Modifier
                            .height(CollapsedHeight)
                            .align(Alignment.TopStart)
                            .graphicsLayer { alpha = 1f - expansion.value },
                    )
                }
                if (expansion.value > 0f) {
                    ExpandedPlayerContent(
                        uiState = uiState,
                        onCollapse = { scope.launch { expansion.animateTo(0f, tween(250)) } },
                        onGoToAlbum = onGoToAlbum,
                        onGoToArtist = onGoToArtist,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = expansion.value
                                val scale = 0.96f + 0.04f * expansion.value
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedPlayerContent(
    uiState: PlayerUiState,
    onCollapse: () -> Unit,
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (Long) -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val track = uiState.currentTrack ?: return
    var showMoreSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.ExpandMore, contentDescription = "Collapse player")
            }
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { showMoreSheet = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
            }
        }

        ArtworkPager(
            queueTracks = uiState.queueTracks,
            currentTrackId = track.id,
            onSkipNext = viewModel::onSkipNext,
            onSkipPrevious = viewModel::onSkipPrevious,
            onJumpToQueueIndex = viewModel::onJumpToQueueIndex,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .aspectRatio(1f),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = viewModel::onToggleFavorite) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        TrackSlider(
            positionMs = uiState.playback.positionMs,
            durationMs = uiState.playback.durationMs,
            onSeek = viewModel::onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        PlaybackControlsRow(
            isPlaying = uiState.playback.isPlaying,
            shuffleEnabled = uiState.playback.shuffleEnabled,
            repeatMode = uiState.playback.repeatMode,
            onTogglePlayPause = viewModel::onTogglePlayPause,
            onSkipNext = viewModel::onSkipNext,
            onSkipPrevious = viewModel::onSkipPrevious,
            onToggleShuffle = { viewModel.onSetShuffleEnabled(!uiState.playback.shuffleEnabled) },
            onCycleRepeatMode = viewModel::onCycleRepeatMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
        )

        LyricsSection(modifier = Modifier.fillMaxWidth())
    }

    if (showMoreSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = sheetState,
        ) {
            ListItem(
                headlineContent = { Text("Go to album") },
                modifier = Modifier.clickable {
                    showMoreSheet = false
                    onGoToAlbum(track.albumId)
                },
            )
            ListItem(
                headlineContent = { Text("Go to artist") },
                modifier = Modifier.clickable {
                    showMoreSheet = false
                    onGoToArtist(track.artistId)
                },
            )
        }
    }
}

/**
 * Horizontal artwork swipe over the queue, two-way synced with actual playback position.
 *
 * A user-initiated page settle issues the matching transport command ([onSkipNext]/
 * [onSkipPrevious] for an adjacent page, [onJumpToQueueIndex] otherwise); a playback change that
 * originated elsewhere (control buttons, notification, another client) instead moves the pager
 * programmatically, guarded by [isProgrammaticScroll] so the two directions never fight - the
 * Compose-idiomatic equivalent of Fossify's `UserAwarePagerCallback`.
 */
@Composable
private fun ArtworkPager(
    queueTracks: List<Track>,
    currentTrackId: Long,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queueTracks.isEmpty()) {
        AsyncImage(
            model = null,
            contentDescription = null,
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }

    val currentIndex = queueTracks.indexOfFirst { it.id == currentTrackId }.let { if (it >= 0) it else 0 }
    val pagerState = rememberPagerState(initialPage = currentIndex) { queueTracks.size }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    val latestCurrentIndex by rememberUpdatedState(currentIndex)

    // Move the pager to match a track change that came from somewhere else (skip buttons,
    // notification actions, ...). Adjacent moves animate; anything else snaps instantly - mirrors
    // Fossify's `PagerCommand` distinction between an adjacent skip and any other jump.
    LaunchedEffect(currentIndex, queueTracks.size) {
        if (pagerState.currentPage == currentIndex) return@LaunchedEffect
        isProgrammaticScroll = true
        try {
            if (abs(pagerState.currentPage - currentIndex) == 1) {
                pagerState.animateScrollToPage(currentIndex)
            } else {
                pagerState.scrollToPage(currentIndex)
            }
        } finally {
            isProgrammaticScroll = false
        }
    }

    // Only a genuine user swipe should issue a transport command - a settle caused by the effect
    // above must not loop back into another command.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (isProgrammaticScroll) return@collect
            if (settled == latestCurrentIndex) return@collect
            when (settled - latestCurrentIndex) {
                1 -> onSkipNext()
                -1 -> onSkipPrevious()
                else -> onJumpToQueueIndex(settled)
            }
        }
    }

    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        AsyncImage(
            model = queueTracks[page].coverArtUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/** Track position slider - commits the seek on release, not on every drag frame. */
@Composable
private fun TrackSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingValue by remember { mutableFloatStateOf(-1f) }
    val maxValue = durationMs.coerceAtLeast(1L).toFloat()
    val displayedValue = if (draggingValue >= 0f) draggingValue else positionMs.toFloat().coerceIn(0f, maxValue)

    Column(modifier = modifier) {
        Slider(
            value = displayedValue,
            valueRange = 0f..maxValue,
            onValueChange = { draggingValue = it },
            onValueChangeFinished = {
                onSeek(draggingValue.toLong())
                draggingValue = -1f
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatMs(displayedValue.toLong()), style = MaterialTheme.typography.labelSmall)
            Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PlaybackControlsRow(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleEnabled) activeColor else inactiveColor,
            )
        }
        IconButton(onClick = onSkipPrevious) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
        }
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = onSkipNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next")
        }
        IconButton(onClick = onCycleRepeatMode) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Repeat mode: $repeatMode",
                tint = if (repeatMode == RepeatMode.OFF) inactiveColor else activeColor,
            )
        }
    }
}

/**
 * Structurally present but honestly empty - Sona has no lyrics data source yet. Not wired to any
 * "reveal" interaction since there is nothing behind it to reveal; a later phase slots real
 * (plain or synced) lyrics content in here without needing to restructure this screen.
 */
@Composable
private fun LyricsSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        HorizontalDivider()
        Text(
            text = "Lyrics",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Text(
            text = "Lyrics not available for this track.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
