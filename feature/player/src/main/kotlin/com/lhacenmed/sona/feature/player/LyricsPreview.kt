@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import androidx.compose.runtime.remember
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.designsystem.theme.pressMorphingClip
import com.lhacenmed.sona.core.model.Track

/** The size the line is drawn at, in sp - the player's own, whatever size the sheet's lyrics are set to. */
private const val LyricsPreviewTextSize = 15f

/** How quiet the line is beneath the title - quieter than the artist, so the title leads. */
private const val LyricsPreviewAlpha = 0.56f

/**
 * The line playing, beneath the title - for lyrics that open in a sheet of their own, which a tap on it opens. It is
 * drawn, lit and moved on from as the sheet draws its lines, quieter than the title, the lines before and after it seen
 * only as they slide through its faded top and bottom.
 *
 * It is one row tall - a line too long for it cut short with an ellipsis - between a faded half row above and below,
 * so the player around it stays where it is whatever the track holds: while its lyrics are read, looked for or
 * missing, it says so in their place, as the sheet does.
 */
@Composable
internal fun LyricsPreview(
    track: Track,
    lyrics: TrackLyrics,
    positionMsProvider: () -> Long,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    textColor: Color,
    onOpen: () -> Unit,
    lyricsViewModel: LyricsViewModel,
    modifier: Modifier = Modifier,
) {
    // Rows of one height, line-timed or word-timed.
    val style =
        rememberLyricsStyle(lyricsViewModel).copy(textSize = LyricsPreviewTextSize, lineSpacing = LyricsWordLineHeight)
    val timeline = lyrics.timeline
    val lineHeight = with(LocalDensity.current) { (LyricsPreviewTextSize * LyricsWordLineHeight).sp.toDp() }
    val fadeHeight = lineHeight / 2
    val lineColor = textColor.copy(alpha = LyricsPreviewAlpha)

    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        // On the keyline, pressed as an expressive button is, its text standing in from its edges as the title's does.
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding)
                .height(lineHeight + fadeHeight * 2)
                .pressMorphingClip(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClickLabel = stringResource(R.string.player_lyrics),
                    onClick = onOpen,
                ),
    ) {
        when {
            timeline == null -> {
                Box(Modifier.fillMaxWidth().padding(horizontal = LyricsLineHorizontalPadding)) {
                    ShimmerHost {
                        TextPlaceholder()
                    }
                }
            }

            timeline.entries.isEmpty() -> {
                // The search and its end cross-fade in place, as in the sheet.
                Crossfade(targetState = lyrics.isFinding, label = "lyricsPreviewFinding") { isFinding ->
                    LyricsPreviewStatus(
                        isFinding = isFinding,
                        track = track,
                        textColor = lineColor,
                        lyricsViewModel = lyricsViewModel,
                    )
                }
            }

            else -> {
                LyricsPreviewLines(
                    timeline = timeline,
                    positionMsProvider = positionMsProvider,
                    sliderPositionProvider = sliderPositionProvider,
                    lyricsSyncOffset = lyricsSyncOffset,
                    style = style,
                    lineHeight = lineHeight,
                    fadeHeight = fadeHeight,
                    textColor = lineColor,
                )
            }
        }
    }
}

/**
 * The lyrics as the sheet lays them out, each line given [lineHeight] and centred in it, the one playing held
 * between the [fadeHeight] faded at the top and the bottom - followed through, never scrolled by hand.
 */
@Composable
private fun LyricsPreviewLines(
    timeline: LyricsTimeline,
    positionMsProvider: () -> Long,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    style: LyricsStyle,
    lineHeight: Dp,
    fadeHeight: Dp,
    textColor: Color,
) {
    val playhead = rememberLyricsPlayhead(timeline, positionMsProvider, sliderPositionProvider, lyricsSyncOffset)
    val lyricsDirection = rememberLyricsDirection(timeline.entries, style)
    val listState = rememberLazyListState()

    // The list's own top padding is the faded row, so the line playing is held right below it.
    FollowLine(
        listState = listState,
        timeline = timeline,
        lineIndex = playhead.lineIndex,
        isFollowing = timeline.isSynced && playhead.lineIndex in timeline.entries.indices,
        anchorOffsetPx = { 0 },
    )

    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        // Each line stands its text in by its own padding, within the keyline the row stands on.
        contentPadding = PaddingValues(vertical = fadeHeight),
        modifier =
            Modifier
                .fillMaxSize()
                .smoothFadingEdge(vertical = fadeHeight)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        itemsIndexed(
            items = timeline.entries,
            key = { index, entry -> "${index}_${entry.time}" },
            contentType = { _, entry -> entry.lyricsContentType(timeline.isSynced) },
        ) { index, item ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(lineHeight)
                    .wrapContentHeight(unbounded = true),
            ) {
                if (item != HEAD_LYRICS_ENTRY) {
                    LyricsLine(
                        index = index,
                        item = item,
                        timeline = timeline,
                        playhead = playhead,
                        style = style,
                        lyricsDirection = lyricsDirection,
                        textColor = textColor,
                        neighbours = NeighbourLines.Hidden,
                        isSelected = false,
                        isSingleLine = true,
                    )
                }
            }
        }
    }
}

/** No lines to show: being looked for on the web, or none found - with the way to look for them again. In [textColor], already quiet. */
@Composable
private fun LyricsPreviewStatus(
    isFinding: Boolean,
    track: Track,
    textColor: Color,
    lyricsViewModel: LyricsViewModel,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LyricsLineHorizontalPadding),
    ) {
        if (isFinding) {
            CircularWavyProgressIndicator(
                color = textColor,
                trackColor = textColor.copy(alpha = 0.2f),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.player_lyrics_finding),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = stringResource(R.string.player_lyrics_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            FindLyricsButton(track = track, lyricsViewModel = lyricsViewModel)
        }
    }
}
