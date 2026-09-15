package com.lhacenmed.sona.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.model.Track
import kotlin.math.abs
import kotlinx.coroutines.delay

private const val DoubleTapSeekStepMs = 5_000L
private val ThumbnailCornerRadius = 16.dp

private data class ThumbnailPage(
    val slotKey: String,
    val track: Track,
)

/**
 * The Classic, Minimal and Cinematic players' artwork: the previous, current and next covers side by side,
 * swiped to change track and double-tapped on either half to seek. Ported from ArchiveTune's `Thumbnail`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Thumbnail(
    uiState: PlayerUiState,
    durationMs: Long,
    textBackgroundColor: Color,
    isPlayerExpanded: Boolean,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val currentTrack = uiState.currentTrack ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val playback = uiState.playback
    val latestPlayback by rememberUpdatedState(playback)
    val latestDurationMs by rememberUpdatedState(durationMs)

    val thumbnailLazyGridState = rememberLazyGridState()

    val previousTrack = uiState.queue.getOrNull(uiState.currentQueueIndex - 1)?.track
    val nextTrack =
        if (uiState.currentQueueIndex >= 0) uiState.queue.getOrNull(uiState.currentQueueIndex + 1)?.track else null

    val thumbnailPages =
        buildList {
            if (previousTrack != null) add(ThumbnailPage(slotKey = "previous", track = previousTrack))
            add(ThumbnailPage(slotKey = "current", track = currentTrack))
            if (nextTrack != null) add(ThumbnailPage(slotKey = "next", track = nextTrack))
        }
    val currentMediaIndex = thumbnailPages.indexOfFirst { it.slotKey == "current" }

    val thumbnailSnapLayoutInfoProvider =
        remember(thumbnailLazyGridState) {
            thumbnailSnapLayoutInfoProvider(
                lazyGridState = thumbnailLazyGridState,
                velocityThreshold = 500f,
            )
        }

    val currentItem by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemIndex } }
    val itemScrollOffset by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemScrollOffset } }

    // Handle swipe to change song
    LaunchedEffect(itemScrollOffset) {
        if (!thumbnailLazyGridState.isScrollInProgress || itemScrollOffset != 0 || currentMediaIndex < 0) {
            return@LaunchedEffect
        }

        if (currentItem > currentMediaIndex && latestPlayback.canSkipNext) {
            viewModel.onSkipNext()
        } else if (currentItem < currentMediaIndex && latestPlayback.canSkipPrevious) {
            viewModel.onSkipToPreviousTrack()
        }
    }

    // Update position when song changes
    LaunchedEffect(currentTrack.id, playback.canSkipPrevious, playback.canSkipNext) {
        if (currentMediaIndex in thumbnailPages.indices) {
            thumbnailLazyGridState.animateScrollToItem(currentMediaIndex)
        }
    }

    LaunchedEffect(uiState.currentQueueIndex, currentTrack.id) {
        if (currentMediaIndex >= 0 && currentMediaIndex != currentItem) {
            thumbnailLazyGridState.scrollToItem(currentMediaIndex)
        }
    }

    // Seek on double tap
    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }
    val layoutDirection = LocalLayoutDirection.current
    val seekStepSeconds = (DoubleTapSeekStepMs / 1000).toInt()

    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Now Playing header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.player_now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    color = textBackgroundColor,
                )
                if (currentTrack.album.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTrack.album,
                        style = MaterialTheme.typography.titleMedium,
                        color = textBackgroundColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }

            // Thumbnail content
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                val itemWidth = maxWidth

                LazyHorizontalGrid(
                    state = thumbnailLazyGridState,
                    rows = GridCells.Fixed(1),
                    flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                    userScrollEnabled = isPlayerExpanded,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = thumbnailPages,
                        key = { page -> "${page.slotKey}:${page.track.id}" },
                        contentType = { "thumbnailPage" },
                    ) { page ->
                        Box(
                            modifier =
                                Modifier
                                    .width(itemWidth)
                                    .fillMaxSize()
                                    .padding(horizontal = PlayerHorizontalPadding)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { offset ->
                                                view.performContextClick()
                                                val currentPosition = viewModel.currentPositionMs()
                                                val isBackward =
                                                    (layoutDirection == LayoutDirection.Ltr && offset.x < size.width / 2) ||
                                                        (layoutDirection == LayoutDirection.Rtl && offset.x > size.width / 2)
                                                if (isBackward) {
                                                    viewModel.onSeek((currentPosition - DoubleTapSeekStepMs).coerceAtLeast(0))
                                                    seekDirection = context.getString(R.string.player_seek_backward, seekStepSeconds)
                                                } else {
                                                    viewModel.onSeek((currentPosition + DoubleTapSeekStepMs).coerceAtMost(latestDurationMs))
                                                    seekDirection = context.getString(R.string.player_seek_forward, seekStepSeconds)
                                                }
                                                showSeekEffect = true
                                            },
                                        )
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            SonaCoverImage(
                                coverArtUri = page.track.coverArtUri,
                                contentDescription = null,
                                cornerRadius = ThumbnailCornerRadius,
                                modifier = Modifier.size(itemWidth - (PlayerHorizontalPadding * 2)),
                            )
                        }
                    }
                }
            }
        }

        // Seek effect
        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        AnimatedVisibility(
            visible = showSeekEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = seekDirection,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
            )
        }
    }
}

/*
 * Copyright (C) OuterTune Project
 * Custom SnapLayoutInfoProvider idea belongs to OuterTune
 */
private fun thumbnailSnapLayoutInfoProvider(
    lazyGridState: LazyGridState,
    velocityThreshold: Float,
): SnapLayoutInfoProvider =
    object : SnapLayoutInfoProvider {
        private val layoutInfo: LazyGridLayoutInfo
            get() = lazyGridState.layoutInfo

        override fun calculateApproachOffset(
            velocity: Float,
            decayOffset: Float,
        ): Float = 0f

        override fun calculateSnapOffset(velocity: Float): Float {
            val bounds = calculateSnappingOffsetBounds()

            // Only snap when velocity exceeds threshold
            if (abs(velocity) < velocityThreshold) {
                if (abs(bounds.start) < abs(bounds.endInclusive)) {
                    return bounds.start
                }

                return bounds.endInclusive
            }

            return when {
                velocity < 0 -> bounds.start
                velocity > 0 -> bounds.endInclusive
                else -> 0f
            }
        }

        private fun calculateSnappingOffsetBounds(): ClosedFloatingPointRange<Float> {
            var lowerBoundOffset = Float.NEGATIVE_INFINITY
            var upperBoundOffset = Float.POSITIVE_INFINITY

            layoutInfo.visibleItemsInfo.forEach { item ->
                val offset = calculateDistanceToDesiredSnapPosition(layoutInfo, item)

                // Find item that is closest to the center
                if (offset <= 0 && offset > lowerBoundOffset) {
                    lowerBoundOffset = offset
                }

                // Find item that is closest to center, but after it
                if (offset >= 0 && offset < upperBoundOffset) {
                    upperBoundOffset = offset
                }
            }

            return lowerBoundOffset.rangeTo(upperBoundOffset)
        }
    }

private fun calculateDistanceToDesiredSnapPosition(
    layoutInfo: LazyGridLayoutInfo,
    item: LazyGridItemInfo,
): Float {
    val containerSize =
        layoutInfo.singleAxisViewportSize - layoutInfo.beforeContentPadding - layoutInfo.afterContentPadding

    val desiredDistance = containerSize.toFloat() / 2f - item.size.width.toFloat() / 2f
    val itemCurrentPosition = item.offset.x.toFloat()

    return itemCurrentPosition - desiredDistance
}

private val LazyGridLayoutInfo.singleAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width
