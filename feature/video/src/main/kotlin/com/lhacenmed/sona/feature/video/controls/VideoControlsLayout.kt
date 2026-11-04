package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.videoSafeArea

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
 * Where every control of the player goes, each way the screen stands: the top bar over everything, and beneath it - in
 * landscape - the transport in the middle and one bar along the bottom, or - in portrait - all of it stacked at the
 * foot of the screen, clear of a picture letterboxed in the middle.
 *
 * Kept clear of the system bars and the display cutout as if the bars were always shown, so nothing moves as they hide
 * and come back with the controls. Under a scrub all but the seek bar and its times fade where they stand, so the bar
 * never moves under the finger; [scrubProgress] is read as it draws, composing nothing.
 */
@Composable
internal fun VideoControlsLayout(
    isLandscape: Boolean,
    scrubProgress: State<Float>,
    slots: VideoControlSlots,
    modifier: Modifier = Modifier,
) {
    val fadesAway = Modifier.graphicsLayer { alpha = 1f - scrubProgress.value }
    // The bars' buttons are their own touch targets: Material would otherwise pad each out past its design.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides VideoPlayerTokens.BarButtonSize) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrubProgress.value }.background(VideoPlayerTokens.ScrubDim))
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(VideoPlayerTokens.TopScrimHeight).graphicsLayer { alpha = 1f - scrubProgress.value }.background(VideoPlayerTokens.TopScrim))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(VideoPlayerTokens.BottomScrimHeight).background(VideoPlayerTokens.BottomScrim))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(VideoPlayerTokens.ScrubScrimHeight).graphicsLayer { alpha = scrubProgress.value }.background(VideoPlayerTokens.ScrubBottomScrim))

            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(videoSafeArea)) {
                if (isLandscape) {
                    Box(Modifier.align(Alignment.TopCenter).padding(start = 20.dp, end = 20.dp, top = 14.dp)) { slots.topBar() }
                    slots.transport(fadesAway.align(Alignment.Center))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                            .height(VideoPlayerTokens.BarButtonSize),
                    ) {
                        Box(fadesAway) { slots.leadingActions() }
                        Box(Modifier.width(52.dp).padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) { slots.elapsedTime(Modifier) }
                        slots.seekBar(Modifier.weight(1f))
                        slots.remainingTime(Modifier.width(64.dp).padding(start = 12.dp))
                        Box(fadesAway) { slots.trailingActions() }
                    }
                } else {
                    Box(Modifier.align(Alignment.TopCenter).padding(horizontal = 8.dp)) { slots.topBar() }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            slots.seekBar(Modifier.fillMaxWidth().height(32.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                slots.elapsedTime(Modifier)
                                Spacer(Modifier.weight(1f))
                                slots.remainingTime(Modifier)
                            }
                        }
                        slots.transport(fadesAway.align(Alignment.CenterHorizontally))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = fadesAway.fillMaxWidth()) {
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
