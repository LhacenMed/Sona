package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.player.PlayerControls
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.portraitFootMargin
import com.lhacenmed.sona.feature.video.videoControlsPadding

/** What goes where in [VideoControlsLayout]: the same controls, wherever each way of standing lays them out. */
internal class VideoControlSlots(
    val topBar: @Composable () -> Unit,
    val transport: @Composable (modifier: Modifier) -> Unit,
    val seekBar: @Composable (modifier: Modifier) -> Unit,
    val times: @Composable (modifier: Modifier) -> Unit,
    val elapsedTime: @Composable (modifier: Modifier) -> Unit,
    val endTime: @Composable (modifier: Modifier) -> Unit,
    val leadingActions: @Composable () -> Unit,
    val trailingActions: @Composable () -> Unit,
)

/**
 * Where every control of the player goes - the same ones, in the same order, however the screen stands: the top bar along
 * the top, then the transport and the foot. Landscape has the width to hold the foot in one row - the lock, the seek bar
 * between its times, and the rest - and keeps the transport in the middle of the picture. Portrait lays the foot out as the
 * music player does, beneath the picture: the seek bar with its [VideoControlSlots.times] beneath it, the transport, then
 * the lock and the rest.
 *
 * What is text stands in line with what is an icon: a button's icon is inset within its touch target, so the seek row keeps
 * further in than the buttons by just that much. Kept clear of the system bars and the display cutout as if the bars were
 * always shown, so nothing moves as they hide and come back with the controls.
 */
@Composable
internal fun VideoControlsLayout(
    isLandscape: Boolean,
    slots: VideoControlSlots,
    modifier: Modifier = Modifier,
) {
    // The bars' buttons are their own touch targets: Material would otherwise pad each out past its design.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides VideoPlayerTokens.BarButtonSize) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(VideoPlayerTokens.TopScrimHeight).background(VideoPlayerTokens.TopScrim))
            if (isLandscape) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(VideoPlayerTokens.BottomScrimHeight).background(VideoPlayerTokens.BottomScrim))
            } else {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(portraitFootMargin + VideoPlayerTokens.PortraitControlsHeight + VideoPlayerTokens.PortraitScrimFade)
                        .background(VideoPlayerTokens.PortraitBottomScrim),
                )
            }

            // In the middle of the screen in landscape, as the picture is, whatever the system bars and cutout take of it.
            if (isLandscape) slots.transport(Modifier.align(Alignment.Center))
            Box(modifier = Modifier.fillMaxSize().padding(videoControlsPadding(isLandscape))) {
                val barMargin = if (isLandscape) VideoPlayerTokens.ControlsMargin else VideoPlayerTokens.PortraitKeyline
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = barMargin, end = barMargin, top = if (isLandscape) VideoPlayerTokens.ControlsMargin else 0.dp),
                ) {
                    slots.topBar()
                }
                if (isLandscape) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = VideoPlayerTokens.ControlsMargin, end = VideoPlayerTokens.ControlsMargin, bottom = VideoPlayerTokens.ControlsMargin)
                            .height(VideoPlayerTokens.BarButtonSize),
                    ) {
                        slots.leadingActions()
                        // Wider only for the hours of a long film, rather than cutting them off.
                        slots.elapsedTime(Modifier.widthIn(min = VideoPlayerTokens.ElapsedTimeWidth))
                        slots.seekBar(Modifier.weight(1f).padding(horizontal = VideoPlayerTokens.SeekTimeGap))
                        slots.endTime(Modifier.widthIn(min = VideoPlayerTokens.RemainingTimeWidth))
                        slots.trailingActions()
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = portraitFootMargin),
                    ) {
                        val keyline = Modifier.fillMaxWidth().padding(horizontal = VideoPlayerTokens.PortraitKeyline)
                        slots.seekBar(keyline.height(VideoPlayerTokens.PortraitSeekBarHeight))
                        slots.times(keyline.height(VideoPlayerTokens.PortraitTimesHeight))
                        Spacer(Modifier.height(VideoPlayerTokens.PortraitTransportTopGap))
                        slots.transport(keyline.height(PlayerControls.PlayButtonSize))
                        Spacer(Modifier.height(VideoPlayerTokens.PortraitTransportBottomGap))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = VideoPlayerTokens.PortraitKeyline)
                                .height(VideoPlayerTokens.BarButtonSize),
                        ) {
                            slots.leadingActions()
                            Spacer(Modifier.weight(1f))
                            slots.trailingActions()
                        }
                    }
                }
            }
        }
    }
}
