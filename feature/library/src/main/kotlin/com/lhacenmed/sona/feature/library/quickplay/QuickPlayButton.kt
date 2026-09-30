package com.lhacenmed.sona.feature.library.quickplay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import com.lhacenmed.sona.core.data.quickplay.QuickPlay
import com.lhacenmed.sona.core.data.quickplay.QuickPlaySource
import com.lhacenmed.sona.core.datastore.QuickPlayMode
import com.lhacenmed.sona.core.designsystem.component.fab.FloatingActionButtonMenuChoice
import com.lhacenmed.sona.core.designsystem.component.fab.FloatingActionButtonMenuContent
import com.lhacenmed.sona.core.designsystem.component.fab.SonaFloatingActionButtonMenu
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.PlaybackParent

/**
 * The library's quick play button - Auxio's home shuffle FAB, which plays [quickPlay]'s source as its mode
 * says: shuffled or in order, its icon saying which. A tap plays exactly what it shows. A long press opens its
 * menu to play another: every track, Favorites, the two listening histories, the collection chosen in the
 * settings while that is the source, or Other, which opens the settings to choose any collection. The source
 * is marked.
 *
 * Picking one makes it the source and plays it at once, so what the button plays from then on is always what
 * it last played. It stands at the bottom of the screen's FAB stack - see [SonaFloatingActionButtonMenu] - and
 * the stack reads [quickPlay] and [visible] where it draws it, so both read state: a change to either is drawn
 * in the frame it happens in. [quickPlay] is null while the user has turned the button off.
 */
@Composable
internal fun QuickPlayButton(
    quickPlay: () -> QuickPlay?,
    visible: () -> Boolean,
    favoritesPlaylistId: Long,
    onPlay: (QuickPlay) -> Unit,
    onPlayFrom: (PlaybackParent?, QuickPlayMode) -> Unit,
    onChooseOther: () -> Unit,
) {
    val favorites = PlaybackParent.Playlist(favoritesPlaylistId)
    SonaFloatingActionButtonMenu {
        val current = quickPlay() ?: return@SonaFloatingActionButtonMenu null
        val source = current.source
        val playFrom: (PlaybackParent?) -> Unit = { parent -> onPlayFrom(parent, current.mode) }
        FloatingActionButtonMenuContent(
            icon = when (current.mode) {
                QuickPlayMode.SHUFFLE -> SonaIcons.Shuffle
                QuickPlayMode.PLAY -> SonaIcons.Play
            },
            contentDescription = when (source) {
                QuickPlaySource.AllTracks -> "${current.mode.verb} all"
                else -> "${current.mode.verb} ${source.label}"
            },
            visible = visible(),
            onClick = { onPlay(current) },
            choices = listOfNotNull(
                FloatingActionButtonMenuChoice(
                    label = QuickPlaySource.AllTracks.label,
                    icon = SonaIcons.Song,
                    isSelected = source == QuickPlaySource.AllTracks,
                    onClick = { playFrom(null) },
                ),
                FloatingActionButtonMenuChoice(
                    label = "Favorites",
                    icon = Icons.Filled.Favorite,
                    isSelected = source.parent == favorites,
                    onClick = { playFrom(favorites) },
                ),
                FloatingActionButtonMenuChoice(
                    label = QuickPlaySource.RecentlyPlayed.label,
                    icon = Icons.Filled.History,
                    isSelected = source == QuickPlaySource.RecentlyPlayed,
                    onClick = { playFrom(PlaybackParent.RecentlyPlayed) },
                ),
                FloatingActionButtonMenuChoice(
                    label = QuickPlaySource.MostPlayed.label,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    isSelected = source == QuickPlaySource.MostPlayed,
                    onClick = { playFrom(PlaybackParent.MostPlayed) },
                ),
                if (source is QuickPlaySource.Collection && source.parent != favorites) {
                    FloatingActionButtonMenuChoice(
                        label = source.name,
                        icon = source.parent.quickPlaySourceKind?.icon ?: SonaIcons.Playlist,
                        isSelected = true,
                        onClick = { playFrom(source.parent) },
                    )
                } else {
                    null
                },
                FloatingActionButtonMenuChoice(
                    label = "Other",
                    icon = Icons.Filled.MoreHoriz,
                    onClick = onChooseOther,
                ),
            ),
        )
    }
}

private val QuickPlayMode.verb: String
    get() = when (this) {
        QuickPlayMode.SHUFFLE -> "Shuffle"
        QuickPlayMode.PLAY -> "Play"
    }

private val QuickPlaySource.label: String
    get() = when (this) {
        QuickPlaySource.AllTracks -> "All tracks"
        QuickPlaySource.RecentlyPlayed -> "Recently played"
        QuickPlaySource.MostPlayed -> "Most played"
        is QuickPlaySource.Collection -> name
    }
