package com.lhacenmed.sona.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle

/**
 * The room a list under [ShuffleAllButton] keeps at its end, so its last row can scroll clear of the
 * button: the button's height and a gap - Auxio's `recycler_fab_space_normal`, measured here from the
 * player's clearance, which the button sits on.
 */
internal val ShuffleAllButtonListSpace = 56.dp + SonaComponentStyle.ContentHorizontalPadding

/**
 * The library's button for shuffling every track - Auxio's home shuffle FAB. It grows in and shrinks
 * away rather than appearing and vanishing, the way a floating action button shows and hides.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShuffleAllButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) +
            fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) +
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        FloatingActionButton(onClick = onClick) {
            Icon(SonaIcons.Shuffle, contentDescription = "Shuffle all")
        }
    }
}
