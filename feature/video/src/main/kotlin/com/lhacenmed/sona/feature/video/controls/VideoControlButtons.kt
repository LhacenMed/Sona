@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.video.controls

import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.designsystem.player.PlayerBarButton
import com.lhacenmed.sona.core.designsystem.player.playerButton
import com.lhacenmed.sona.feature.video.VideoPlayerTokens

/**
 * One control of the player - what it shows, what it is called, and what it does. Every bar and the More panel are
 * lists of these, so a control is added, moved or dropped by editing a list.
 *
 * [onClick] is null for a control that cannot act right now. With [checked], it is a toggle, squared in the player's
 * checked colours while on.
 */
@Immutable
internal data class VideoAction(
    val icon: ImageVector,
    val label: String,
    val onClick: (() -> Unit)?,
    val tint: Color = VideoPlayerTokens.ContentColor,
    val checked: Boolean? = null,
)

/** [action] standing alone over the picture - every player's bar control, see [PlayerBarButton]. */
@Composable
internal fun VideoBarButton(action: VideoAction, modifier: Modifier = Modifier) {
    PlayerBarButton(
        icon = action.icon,
        contentDescription = action.label,
        color = action.tint,
        onClick = { action.onClick?.invoke() },
        enabled = action.onClick != null,
        checked = action.checked,
        modifier = modifier,
    )
}

/** [action] in a `PlayerButtonGroup`, beside the controls it stands with. */
internal fun ButtonGroupScope.videoButton(action: VideoAction) =
    playerButton(action.icon, action.label, action.tint, action.onClick, checked = action.checked)
