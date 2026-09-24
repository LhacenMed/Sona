package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.pressedCornerRadius
import com.lhacenmed.sona.core.designsystem.theme.rememberPressFraction

/**
 * The corners a FAB rests with: Material's large corner - `m3_comp_fab_container_shape`, which Auxio's
 * FAB keeps, and what Compose's own FAB rests with.
 */
private val RestingCornerRadius = 16.dp

/**
 * Every floating action button in the app: Material's expressive FAB, shown and hidden by its own
 * motion - [animateFloatingActionButton], growing out of and shrinking into its centre, as Auxio's does -
 * and pressing as everything else in Sona does.
 *
 * Material's FAB takes a single static shape, so on its own it would not answer a press. Its corners
 * are driven here by [rememberPressFraction] instead, resting on Auxio's - Material's FAB corner - and
 * tightening to [SonaComponentStyle.PressedCornerRadius] while held, as every press in Sona does.
 *
 * Hidden, it takes no room and no touches; it stays composed, so showing it again is only its motion.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SonaFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressFraction by rememberPressFraction(interactionSource)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.animateFloatingActionButton(visible = visible, alignment = Alignment.Center),
        shape = RoundedCornerShape(pressedCornerRadius(pressFraction, restingRadius = RestingCornerRadius)),
        interactionSource = interactionSource,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
