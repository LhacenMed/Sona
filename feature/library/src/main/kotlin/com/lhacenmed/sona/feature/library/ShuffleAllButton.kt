package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaFloatingActionButton
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle

/**
 * The room a list under [ShuffleAllButton] keeps at its end, so its last row can scroll clear of the
 * button: the button's height and a gap - Auxio's `recycler_fab_space_normal`, measured here from the
 * player's clearance, which the button sits on.
 */
internal val ShuffleAllButtonListSpace = 56.dp + SonaComponentStyle.ContentHorizontalPadding

/** The library's button for shuffling every track - Auxio's home shuffle FAB. */
@Composable
internal fun ShuffleAllButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SonaFloatingActionButton(
        onClick = onClick,
        icon = SonaIcons.Shuffle,
        contentDescription = "Shuffle all",
        visible = visible,
        modifier = modifier,
    )
}
