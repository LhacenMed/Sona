package com.lhacenmed.sona.core.designsystem.component.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.FavoriteToggle
import com.lhacenmed.sona.core.designsystem.component.SonaFavoriteButton
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.R

/**
 * The top bar button that opens what a screen shows in its options sheet - a detail screen's collection, as its row's
 * button does: its options in the sheet, never in a menu of the bar's own.
 */
@Composable
fun optionsSheetAction(onClick: () -> Unit) = TopBarAction(
    label = stringResource(R.string.list_row_more_options),
    icon = SonaIcons.MoreVert,
    onClick = onClick,
)

/** How faint a disabled option reads, next to the options it sits among: Material's disabled content alpha. */
private const val DISABLED_OPTION_ALPHA = 0.38f

/**
 * The header every options sheet opens with - Auxio's `menuCover`/`menuType`/`menuName`/`menuInfo`: [cover],
 * then [type], [name] and [info] beside it, and the line the header ends on, edge to edge. Given to
 * [SonaBottomSheet] as its header. What can be favorited has its [favorite] heart at the header's end,
 * centred on it - as a detail screen's header has. What can be renamed is, by pressing its name - [onRename] - which
 * answers with no ripple: the name is not a button, it is the thing itself.
 */
@Composable
fun SonaOptionsSheetHeader(
    cover: @Composable () -> Unit,
    type: String,
    name: String,
    info: String,
    favorite: FavoriteToggle? = null,
    onRename: (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier.padding(start = 24.dp, end = if (favorite != null) 12.dp else 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cover()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                // Held to the lines a detail screen's header gives them, however long a name the file holds.
                Text(
                    text = type,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onRename != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onRename) else Modifier,
                )
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (favorite != null) SonaFavoriteButton(favorite)
        }
        // Auxio's `menu_mode_group`: the line the header ends on, edge to edge under the cover.
        HorizontalDivider()
    }
}

/**
 * One option in an options sheet: its [icon] and [label], the [detail] it currently stands at where it has
 * one, and [trailingContent] - a switch, for an option that is turned on and off. Faded, and pressed to
 * nothing, while not [enabled].
 */
@Composable
fun SonaOptionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    detail: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = detail?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailingContent,
        // The sheet already paints its own background; a row painting its own would seam against it instead
        // of reading as one surface.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_OPTION_ALPHA)
            .clickable(enabled = enabled, onClick = onClick),
    )
}
