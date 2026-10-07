package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.R

/** Whether something is in Favorites, and what puts it in or takes it out - what a [SonaFavoriteButton] shows. */
@Immutable
class FavoriteToggle(
    val isFavorite: Boolean,
    val onToggle: () -> Unit,
)

/**
 * The heart that favorites whatever a header shows - a detail screen's, an options sheet's: filled in the
 * primary colour while it is in Favorites, outlined while it is not. A press flips it in place.
 */
@Composable
fun SonaFavoriteButton(favorite: FavoriteToggle, modifier: Modifier = Modifier) {
    SonaIconButton(
        onClick = favorite.onToggle,
        icon = if (favorite.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = stringResource(if (favorite.isFavorite) R.string.favorite_remove else R.string.favorite_add),
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (favorite.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        ),
    )
}
