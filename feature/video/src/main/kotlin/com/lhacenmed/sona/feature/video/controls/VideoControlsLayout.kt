package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.portraitFootMargin
import com.lhacenmed.sona.feature.video.videoSafeArea
import kotlin.math.roundToInt

/** What goes where in [VideoControlsLayout]: the same controls, wherever each way of standing lays them out. */
internal class VideoControlSlots(
    val topBar: @Composable () -> Unit,
    val transport: @Composable (modifier: Modifier) -> Unit,
    val seekBar: @Composable (modifier: Modifier) -> Unit,
    val elapsedTime: @Composable (modifier: Modifier) -> Unit,
    val remainingTime: @Composable (modifier: Modifier) -> Unit,
    val leadingActions: @Composable () -> Unit,
    val trailingActions: @Composable () -> Unit,
)

/**
 * Where every control of the player goes - the same ones, in the same order, however the screen stands: the top bar along the
 * top, the transport in the middle of the picture, and along the foot the lock, the seek bar between its times, and the rest.
 * Landscape has the width to hold the foot in one row; portrait gives the seek bar a row of its own, above the others.
 *
 * What is text stands in line with what is an icon: a button's icon is inset within its touch target, so the seek row keeps
 * further in than the buttons by just that much. Kept clear of the system bars and the display cutout as if the bars were
 * always shown, so nothing moves as they hide and come back with the controls.
 */
@Composable
internal fun VideoControlsLayout(
    isLandscape: Boolean,
    pictureShiftPx: () -> Float,
    slots: VideoControlSlots,
    modifier: Modifier = Modifier,
) {
    // The bars' buttons are their own touch targets: Material would otherwise pad each out past its design.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides VideoPlayerTokens.BarButtonSize) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(VideoPlayerTokens.TopScrimHeight).background(VideoPlayerTokens.TopScrim))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(VideoPlayerTokens.BottomScrimHeight).background(VideoPlayerTokens.BottomScrim))

            val safeArea = if (isLandscape) videoSafeArea else videoSafeArea.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(safeArea)) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = VideoPlayerTokens.ControlsMargin, end = VideoPlayerTokens.ControlsMargin, top = if (isLandscape) VideoPlayerTokens.ControlsMargin else 0.dp),
                ) {
                    slots.topBar()
                }
                if (isLandscape) {
                    slots.transport(Modifier.align(Alignment.Center))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = VideoPlayerTokens.ControlsMargin, end = VideoPlayerTokens.ControlsMargin, bottom = VideoPlayerTokens.ControlsMargin)
                            .height(VideoPlayerTokens.BarButtonSize),
                    ) {
                        slots.leadingActions()
                        SeekRow(slots, Modifier.weight(1f).padding(end = VideoPlayerTokens.ControlsMargin))
                        slots.trailingActions()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = portraitFootMargin),
                    ) {
                        SeekRow(
                            slots,
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = VideoPlayerTokens.ControlsTextMargin)
                                .height(VideoPlayerTokens.PortraitSeekRowHeight),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = VideoPlayerTokens.ControlsMargin)
                                .height(VideoPlayerTokens.BarButtonSize),
                        ) {
                            slots.leadingActions()
                            Spacer(Modifier.weight(1f))
                            slots.trailingActions()
                        }
                    }
                }
            }
            if (!isLandscape) {
                slots.transport(Modifier.align(Alignment.Center).offset { IntOffset(0, pictureShiftPx().roundToInt()) })
            }
        }
    }
}

/** The seek bar between its times. */
@Composable
private fun SeekRow(slots: VideoControlSlots, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        // Wider only for the hours of a long film, rather than cutting them off.
        slots.elapsedTime(Modifier.widthIn(min = VideoPlayerTokens.ElapsedTimeWidth))
        slots.seekBar(Modifier.weight(1f).padding(horizontal = VideoPlayerTokens.SeekTimeGap))
        slots.remainingTime(Modifier.width(VideoPlayerTokens.RemainingTimeWidth))
    }
}
