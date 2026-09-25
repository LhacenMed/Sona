package com.lhacenmed.sona.feature.library.shuffle

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.lhacenmed.sona.core.data.shuffle.ShuffleAllSource
import com.lhacenmed.sona.core.designsystem.component.fab.FloatingActionButtonMenuChoice
import com.lhacenmed.sona.core.designsystem.component.fab.SonaFloatingActionButtonMenu
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.PlaybackParent

/**
 * The library's shuffle button - Auxio's home shuffle FAB. A tap shuffles [source]; a long press opens
 * its menu to shuffle another: every track, Favorites, the collection chosen in the settings while that
 * is the source, or Other, which opens the settings to choose any collection. The source is marked.
 *
 * Picking one makes it the source and shuffles it at once, so what the button plays from then on is
 * always what it last played. It stands at the bottom of the screen's FAB stack - see [SonaFloatingActionButtonMenu].
 */
@Composable
internal fun ShuffleAllButton(
    visible: Boolean,
    source: ShuffleAllSource,
    favoritesPlaylistId: Long,
    onShuffle: () -> Unit,
    onShuffleFrom: (PlaybackParent?) -> Unit,
    onChooseOther: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val favorites = PlaybackParent.Playlist(favoritesPlaylistId)
    val shuffleFrom: (PlaybackParent?) -> Unit = { parent ->
        expanded = false
        onShuffleFrom(parent)
    }

    SonaFloatingActionButtonMenu(
        onClick = onShuffle,
        icon = SonaIcons.Shuffle,
        contentDescription = when (source) {
            ShuffleAllSource.AllTracks -> "Shuffle all"
            is ShuffleAllSource.Collection -> "Shuffle ${source.name}"
        },
        visible = visible,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        choices = listOfNotNull(
            FloatingActionButtonMenuChoice(
                label = "All tracks",
                icon = SonaIcons.Song,
                isSelected = source == ShuffleAllSource.AllTracks,
                onClick = { shuffleFrom(null) },
            ),
            FloatingActionButtonMenuChoice(
                label = "Favorites",
                icon = Icons.Filled.Favorite,
                isSelected = source.parent == favorites,
                onClick = { shuffleFrom(favorites) },
            ),
            if (source is ShuffleAllSource.Collection && source.parent != favorites) {
                FloatingActionButtonMenuChoice(
                    label = source.name,
                    icon = source.parent.shuffleSourceKind?.icon ?: SonaIcons.Shuffle,
                    isSelected = true,
                    onClick = { shuffleFrom(source.parent) },
                )
            } else {
                null
            },
            FloatingActionButtonMenuChoice(
                label = "Other",
                icon = Icons.Filled.MoreHoriz,
                onClick = {
                    expanded = false
                    onChooseOther()
                },
            ),
        ),
    )
}
