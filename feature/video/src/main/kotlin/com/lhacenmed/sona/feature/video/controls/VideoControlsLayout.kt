package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lhacenmed.sona.feature.video.VideoPlayerTokens

/**
 * Where every control of the player goes: a bar along the top and one along the bottom, each on a scrim that
 * reaches the screen's edge, and a rail down either side, centred.
 *
 * Kept clear of the system bars and the display cutout as if the bars were always shown, so nothing moves as
 * they hide and come back with the controls. Each slot is laid out whatever it holds, so the layout is the same
 * locked or not, with a video or its sound alone - but for the top bar, which a locked screen has none of.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoControlsLayout(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    startRail: @Composable () -> Unit = {},
    endRail: @Composable () -> Unit = {},
    bottomBar: @Composable ColumnScope.() -> Unit = {},
) {
    val insets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    Box(modifier = modifier.fillMaxSize()) {
        if (topBar != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(VideoPlayerTokens.TopBarScrim)
                    .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                topBar()
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Start))
                .padding(start = VideoPlayerTokens.EdgePadding),
        ) {
            startRail()
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(insets.only(WindowInsetsSides.End))
                .padding(end = VideoPlayerTokens.EdgePadding),
        ) {
            endRail()
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(VideoPlayerTokens.BottomBarScrim)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            content = bottomBar,
        )
    }
}
