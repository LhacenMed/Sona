package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes

/**
 * Every icon button in the app.
 *
 * The only thing this adds to Material's [IconButton] is [iconButtonPressShapes], which is what
 * makes the container morph from a circle to [SonaComponentStyle.PressedCornerRadius] for as long as it is held. Material's
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
 *
 * [colors] gives the button a container - `IconButtonDefaults.filledTonalIconButtonColors()` or
 * `filledIconButtonColors()` - for one that has to stand out. It changes the paint alone: a filled
 * button morphs, and widens in a group, exactly as a plain one does.
 */
@Composable
fun SonaIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
) {
    IconButton(
        onClick = onClick,
        shapes = iconButtonPressShapes(),
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
