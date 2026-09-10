package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.navigation.LocalNavigator

/**
 * The three collections that are always one tap away, above the browsing tabs.
 *
 * They sit outside the pager on purpose: the tabs are ways of *browsing the library*, while these
 * are destinations of their own - which is also why they keep their place while the tabs are swiped.
 *
 * No counts here. A count would have to be read before the row could be drawn, which would subscribe
 * the pager to library data it otherwise never touches; the destinations show their own counts.
 */
@Composable
fun LibraryShortcuts(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShortcutCard(
            title = "Favorites",
            icon = Icons.Filled.Favorite,
            onClick = { navigator.go(FavoritesScreen) },
        )
        ShortcutCard(
            title = "Playlists",
            icon = Icons.Filled.LibraryMusic,
            onClick = { navigator.go(PlaylistsScreen) },
        )
        ShortcutCard(
            title = "Recent",
            icon = Icons.Filled.History,
            onClick = { navigator.go(RecentlyPlayedScreen) },
        )
    }
}

/** Equal thirds, so the row's shape is fixed regardless of how long a title is. */
@Composable
private fun RowScope.ShortcutCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
