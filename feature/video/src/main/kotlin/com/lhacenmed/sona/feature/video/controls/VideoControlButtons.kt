@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType

/**
 * One control of the player - what it shows, what it is called, and what it does. Every bar and the More panel are
 * lists of these, so a control is added, moved or dropped by editing a list.
 *
 * [onClick] is null for a control that cannot act right now.
 */
@Immutable
internal data class VideoAction(
    val icon: ImageVector,
    val label: String,
    val onClick: (() -> Unit)?,
    val tint: Color = VideoPlayerTokens.ContentColor,
)

/** A control over the picture: Material's icon button, pressing as every press in the app does. */
@Composable
internal fun VideoBarButton(
    action: VideoAction,
    modifier: Modifier = Modifier,
    size: Dp = VideoPlayerTokens.BarButtonSize,
    iconSize: Dp = VideoPlayerTokens.BarIconSize,
) {
    IconButton(
        shapes = iconButtonPressShapes(),
        onClick = { action.onClick?.invoke() },
        enabled = action.onClick != null,
        colors = IconButtonDefaults.iconButtonColors(contentColor = action.tint, disabledContentColor = action.tint.copy(alpha = 0.38f)),
        modifier = modifier.size(size),
    ) {
        Icon(imageVector = action.icon, contentDescription = action.label, modifier = Modifier.size(iconSize))
    }
}

/** A control skipping [seconds] on or back, which its face says. */
@Composable
internal fun VideoSkipButton(icon: ImageVector, seconds: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        shapes = iconButtonPressShapes(),
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = VideoPlayerTokens.ContentColor),
        modifier = modifier.size(VideoPlayerTokens.SkipButtonSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(VideoPlayerTokens.SkipIconSize))
            PlayerText(seconds.toString(), VideoPlayerType.SkipSeconds, modifier = Modifier.graphicsLayer { translationY = 1.dp.toPx() })
        }
    }
}

/** The speed the video plays at, and the way to change it. */
@Composable
internal fun VideoSpeedButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        shapes = buttonPressShapes(),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        modifier = modifier.width(VideoPlayerTokens.SpeedButtonWidth),
    ) {
        PlayerText(label, VideoPlayerType.Speed)
    }
}
