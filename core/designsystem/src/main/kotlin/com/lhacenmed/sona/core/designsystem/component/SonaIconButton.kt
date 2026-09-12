package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every icon button in the app.
 *
 * The only thing this adds to Material's [IconButton] is [IconButtonDefaults.shapes], which is what
 * makes the container morph from a circle to a rounded square for as long as it is held. Material's
 * other overload takes a single static shape instead, so a plain `IconButton` quietly opts out of
 * the press behaviour - and since nothing flags that, having one named button that always passes the
 * shapes is what keeps every icon in the app pressing the same way, rather than leaving each call
 * site to remember.
 *
 * Size is deliberately not set. Material's small icon button is already a 40dp container around a
 * 24dp icon inside a 48dp touch target, which is the sizing this is modelled on.
 *
 * [interactionSource] is here for [SonaIconButtonGroup], which has to watch the same press this
 * button is reacting to. On its own the button has no use for it and lets Material make one.
 */
@Composable
fun SonaIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier,
        interactionSource = interactionSource,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
