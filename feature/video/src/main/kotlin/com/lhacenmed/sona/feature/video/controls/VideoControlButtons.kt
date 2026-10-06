package com.lhacenmed.sona.feature.video.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lhacenmed.sona.feature.video.VideoPlayerTokens

/**
 * One control of the player - what it shows, what it is called, and what it does. Every bar and rail is a list
 * of these, so a control is added, moved or dropped by editing a list.
 *
 * [onClick] is null for a control already in its place whose action is still to come: it is drawn as it will
 * be, and does nothing yet.
 */
@Immutable
internal data class VideoAction(
    val icon: ImageVector,
    val label: String,
    val onClick: (() -> Unit)?,
    val tint: Color = VideoPlayerTokens.ContentColor,
)

/** A control standing on its own over the picture, on a scrim of its own - the side rails'. */
@Composable
internal fun VideoRailButton(action: VideoAction, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(VideoPlayerTokens.ControlCornerRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(VideoPlayerTokens.ControlSize)
            .clip(shape)
            .background(VideoPlayerTokens.ControlScrim)
            .then(
                action.onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) }
                    ?: Modifier.semantics { contentDescription = action.label },
            ),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            tint = action.tint,
            modifier = Modifier.size(VideoPlayerTokens.ControlIconSize),
        )
    }
}

/** A control within a bar, on the bar's own scrim. */
@Composable
internal fun VideoBarButton(action: VideoAction, modifier: Modifier = Modifier) {
    IconButton(
        onClick = { action.onClick?.invoke() },
        enabled = action.onClick != null,
        // A control with nothing to do yet looks as it will, rather than greyed out as if it could not be used.
        colors = IconButtonDefaults.iconButtonColors(contentColor = action.tint, disabledContentColor = action.tint),
        modifier = modifier,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            modifier = Modifier.size(VideoPlayerTokens.ControlIconSize),
        )
    }
}

/**
 * A column of [VideoRailButton]s down one side of the screen. A null keeps its place empty, so a control can
 * be hidden - while the controls are locked, say - without the others moving.
 */
@Composable
internal fun VideoControlRail(actions: List<VideoAction?>, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(VideoPlayerTokens.RailSpacing),
        modifier = modifier,
    ) {
        actions.forEach { action ->
            if (action != null) {
                VideoRailButton(action)
            } else {
                Spacer(Modifier.size(VideoPlayerTokens.ControlSize))
            }
        }
    }
}
