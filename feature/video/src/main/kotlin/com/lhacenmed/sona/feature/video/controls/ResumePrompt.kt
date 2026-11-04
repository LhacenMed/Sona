@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.video.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType
import com.lhacenmed.sona.feature.video.formatVideoTime
import com.lhacenmed.sona.feature.video.portraitFootMargin
import com.lhacenmed.sona.feature.video.videoSafeArea
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/**
 * Whether the video just resumed still has something to say about it: from when it comes back from [resumedFromMs] until it
 * has played on a few seconds, the user has moved it themselves, or the controls have come and gone.
 */
@Composable
internal fun rememberIsResumePromptDue(resumedFromMs: Long?, isPlaying: Boolean, isScrubbing: Boolean, areControlsVisible: Boolean): Boolean {
    var isDue by remember(resumedFromMs) { mutableStateOf(resumedFromMs != null) }
    LaunchedEffect(isDue, isPlaying) {
        if (isDue && isPlaying) {
            delay(VideoPlayerTokens.ResumePromptTimeoutMs)
            isDue = false
        }
    }
    LaunchedEffect(isScrubbing) {
        if (isScrubbing) isDue = false
    }
    val latestAreControlsVisible by rememberUpdatedState(areControlsVisible)
    LaunchedEffect(resumedFromMs) {
        snapshotFlow { latestAreControlsVisible }.drop(1).filter { !it }.collect { isDue = false }
    }
    return isDue
}

/**
 * That the video came back from where it was left, and the way to start it over instead - standing on its own just above where
 * the controls' foot does, as it has to be seen whether they are up or not. In line with the times, however the screen stands.
 */
@Composable
internal fun ResumePrompt(resumedFromMs: Long?, isVisible: Boolean, isLandscape: Boolean, onStartOver: () -> Unit) {
    // A plain holder: what it says stays as it was while it fades away.
    val shownMs = remember { longArrayOf(0L) }
    if (resumedFromMs != null) shownMs[0] = resumedFromMs
    val safeArea = if (isLandscape) videoSafeArea else videoSafeArea.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val footHeight = if (isLandscape) {
        VideoPlayerTokens.ControlsMargin + VideoPlayerTokens.BarButtonSize
    } else {
        portraitFootMargin + VideoPlayerTokens.PortraitControlsHeight
    }
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(safeArea)
            .padding(start = VideoPlayerTokens.ControlsTextMargin, bottom = footHeight + VideoPlayerTokens.ResumePromptGap),
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(VideoPlayerTokens.ResumePromptFadeMs)) + slideInVertically(tween(VideoPlayerTokens.ResumePromptFadeMs)) { it / 6 },
            exit = fadeOut(tween(VideoPlayerTokens.ResumePromptFadeMs)) + slideOutVertically(tween(VideoPlayerTokens.ResumePromptFadeMs)) { it / 6 },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(VideoPlayerTokens.ResumePromptHeight)
                    .background(VideoPlayerTokens.ResumePromptColor, CircleShape)
                    .padding(start = 14.dp, end = 4.dp),
            ) {
                PlayerText(stringResource(R.string.video_resumed_from), VideoPlayerType.PromptLabel, color = VideoPlayerTokens.SecondaryContent)
                PlayerText(formatVideoTime(shownMs[0]), VideoPlayerType.PromptTime, modifier = Modifier.padding(start = 4.dp))
                Spacer(
                    Modifier
                        .padding(start = 10.dp, end = 4.dp)
                        .size(width = 1.dp, height = 16.dp)
                        .background(VideoPlayerTokens.ResumePromptDivider),
                )
                // As tall as the pill to the finger, whatever it looks.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides VideoPlayerTokens.ResumePromptHeight) {
                    TextButton(
                        onClick = onStartOver,
                        shapes = buttonPressShapes(),
                        colors = ButtonDefaults.textButtonColors(contentColor = VideoPlayerTokens.ContentColor),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(28.dp),
                    ) {
                        PlayerText(stringResource(R.string.video_start_over), VideoPlayerType.PromptTime)
                    }
                }
            }
        }
    }
}
