package com.lhacenmed.sona.feature.video.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType
import com.lhacenmed.sona.feature.video.controls.VideoAction
import com.lhacenmed.sona.feature.video.controls.VideoBarButton
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons

/** The panels the player opens over itself. */
internal enum class VideoPanel(val width: Dp) {
    TRACKS(VideoPlayerTokens.TracksPanelWidth),
    QUEUE(VideoPlayerTokens.ListPanelWidth),
    SPEED(VideoPlayerTokens.ListPanelWidth),
    MORE(VideoPlayerTokens.ListPanelWidth),
    CHAPTERS(VideoPlayerTokens.ListPanelWidth),
}

/**
 * Whichever [VideoPanel] is open: every panel of the player shares this one frame and motion, sliding in from the
 * edge beside the picture - not a window of its own, so the video plays on beside it and its subtitles can be judged
 * as they are changed. The last one stays drawn as it slides away.
 */
@Composable
internal fun BoxScope.PlayerPanelHost(
    panel: VideoPanel?,
    isLandscape: Boolean,
    content: @Composable ColumnScope.(VideoPanel) -> Unit,
) {
    // A plain holder rather than state: what is shown follows [panel] in the same composition.
    val last = remember { arrayOfNulls<VideoPanel>(1) }
    if (panel != null) last[0] = panel
    val fadeMs = VideoPlayerTokens.ChromeFadeMs
    AnimatedVisibility(
        visible = panel != null,
        enter = fadeIn(tween(fadeMs)) + if (isLandscape) slideInHorizontally(tween(fadeMs)) { it / 6 } else slideInVertically(tween(fadeMs)) { it / 6 },
        exit = fadeOut(tween(fadeMs)) + if (isLandscape) slideOutHorizontally(tween(fadeMs)) { it / 6 } else slideOutVertically(tween(fadeMs)) { it / 6 },
        modifier = Modifier.align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter),
    ) {
        last[0]?.let { shown -> PanelFrame(shown.width, isLandscape) { content(shown) } }
    }
}

/**
 * The panel's ground. Its colours are handed down as the theme's, so rows the app draws elsewhere - the queue's -
 * read as the player's on it, whatever the app's theme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelFrame(width: Dp, isLandscape: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(VideoPlayerTokens.PanelCornerRadius)
    val insets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = VideoPlayerTokens.Accent,
            surface = Color.Transparent,
            onSurface = VideoPlayerTokens.ContentColor,
            onSurfaceVariant = VideoPlayerTokens.SecondaryContent,
            secondaryContainer = VideoPlayerTokens.SegmentChosen,
            onSecondaryContainer = VideoPlayerTokens.ContentColor,
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .windowInsetsPadding(insets)
                .padding(VideoPlayerTokens.PanelMargin)
                .then(if (isLandscape) Modifier.width(width).fillMaxHeight() else Modifier.fillMaxWidth().heightIn(max = windowHeight * 0.8f))
                .shadow(24.dp, shape)
                .clip(shape)
                .background(VideoPlayerTokens.PanelColor)
                // A tap on the panel's ground stays here, rather than reaching the video beneath and closing it.
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
            content = content,
        )
    }
}

/** A panel's name and the way to close it. */
@Composable
internal fun PanelHeader(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(VideoPlayerTokens.BarButtonSize)) {
        PlayerText(title, VideoPlayerType.Title, modifier = Modifier.weight(1f))
        VideoBarButton(VideoAction(PlayerIcons.Close, stringResource(R.string.video_panel_close), onClose))
    }
}

@Composable
internal fun PanelSectionTitle(title: String) {
    PlayerText(
        text = title.uppercase(),
        style = VideoPlayerType.PanelSection,
        color = VideoPlayerTokens.TertiaryContent,
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
    )
}

/** One of a set of which one is chosen: marked by a tick, and brighter than the rest. */
@Composable
internal fun PanelChoiceRow(label: String, isChosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(VideoPlayerTokens.PanelRowHeight)
            .clip(RoundedCornerShape(VideoPlayerTokens.PanelRowCornerRadius))
            .selectable(selected = isChosen, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp),
    ) {
        if (isChosen) {
            Icon(PlayerIcons.Check, contentDescription = null, tint = VideoPlayerTokens.Accent, modifier = Modifier.size(18.dp))
        } else {
            Spacer(Modifier.size(18.dp))
        }
        PlayerText(
            text = label,
            style = VideoPlayerType.PanelRow,
            color = if (isChosen) VideoPlayerTokens.ContentColor else VideoPlayerTokens.UnchosenContent,
        )
    }
}

/** Something to do rather than to choose. */
@Composable
internal fun PanelActionRow(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = VideoPlayerTokens.ContentColor,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(VideoPlayerTokens.PanelRowHeight)
            .clip(RoundedCornerShape(VideoPlayerTokens.PanelRowCornerRadius))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier.alpha(0.38f))
            .padding(horizontal = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        PlayerText(label, VideoPlayerType.PanelRow, color = color)
    }
}

/** A pill holding a [content] of small controls. */
@Composable
internal fun PanelSegmentGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(VideoPlayerTokens.SegmentGround, CircleShape)
            .padding(2.dp),
    ) {
        content()
    }
}

@Composable
internal fun PanelDivider() {
    Box(
        Modifier
            .padding(top = 4.dp, bottom = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(VideoPlayerTokens.PanelDivider),
    )
}
