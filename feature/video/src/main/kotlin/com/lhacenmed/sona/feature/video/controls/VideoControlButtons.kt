@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaIconButtonGroup
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

@Composable
private fun videoButtonColors(tint: Color): IconButtonColors =
    IconButtonDefaults.iconButtonColors(contentColor = tint, disabledContentColor = tint.copy(alpha = 0.38f))

/** A control standing alone over the picture: Material's icon button, pressing as every press in the app does. */
@Composable
internal fun VideoBarButton(action: VideoAction, modifier: Modifier = Modifier, iconSize: Dp = VideoPlayerTokens.BarIconSize) {
    IconButton(
        shapes = iconButtonPressShapes(),
        onClick = { action.onClick?.invoke() },
        enabled = action.onClick != null,
        colors = videoButtonColors(action.tint),
        modifier = modifier.size(VideoPlayerTokens.BarButtonSize),
    ) {
        Icon(imageVector = action.icon, contentDescription = action.label, modifier = Modifier.size(iconSize))
    }
}

/** Controls standing together, a held one widening at its neighbours' expense - see [SonaIconButtonGroup]. */
@Composable
internal fun VideoButtonGroup(modifier: Modifier = Modifier, spacing: Dp = 0.dp, content: ButtonGroupScope.() -> Unit) {
    SonaIconButtonGroup(modifier = modifier, spacing = spacing, content = content)
}

/** [action] in a [VideoButtonGroup]. */
internal fun ButtonGroupScope.videoButton(
    action: VideoAction,
    size: Dp = VideoPlayerTokens.BarButtonSize,
    iconSize: Dp = VideoPlayerTokens.BarIconSize,
) = videoGroupItem(action.label, action.onClick, action.tint, size) {
    Icon(imageVector = action.icon, contentDescription = action.label, modifier = Modifier.size(iconSize))
}

/** The speed the video plays at, and the way to change it: a control the size of any other, saying [speed] for an icon. */
internal fun ButtonGroupScope.speedButton(speed: String, label: String, onClick: () -> Unit) =
    videoGroupItem(label, onClick, VideoPlayerTokens.ContentColor, VideoPlayerTokens.BarButtonSize) {
        PlayerText(speed, VideoPlayerType.Speed)
    }

private fun ButtonGroupScope.videoGroupItem(
    label: String,
    onClick: (() -> Unit)?,
    tint: Color,
    size: Dp,
    content: @Composable () -> Unit,
) = customItem(
    buttonGroupContent = {
        val interactionSource = remember { MutableInteractionSource() }
        IconButton(
            shapes = iconButtonPressShapes(),
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            colors = videoButtonColors(tint),
            interactionSource = interactionSource,
            // The group's width comes first, and this gives way to it: all a press has to grow into.
            modifier = Modifier.animateWidth(interactionSource).size(size),
            content = content,
        )
    },
    menuContent = { menuState ->
        DropdownMenuItem(
            text = { Text(label) },
            onClick = {
                menuState.dismiss()
                onClick?.invoke()
            },
            enabled = onClick != null,
        )
    },
)
