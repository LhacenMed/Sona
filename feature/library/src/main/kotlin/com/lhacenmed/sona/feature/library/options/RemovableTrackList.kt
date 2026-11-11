package com.lhacenmed.sona.feature.library.options

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.component.title
import com.lhacenmed.sona.core.model.Playlist as PlaylistModel
import com.lhacenmed.sona.feature.library.R

/**
 * A list tracks can be taken out of, their files untouched: a playlist, or one of the listening histories. What a
 * track's options sheet and a list's selection bar offer to remove from, and what the dialog asking first says.
 */
sealed interface RemovableTrackList {
    /** What removing from it is offered as. */
    val removeAction: OptionsAction

    /** What follows from removing from it, as the dialog asking first says. */
    @get:Composable
    val removalMessage: String

    data class Playlist(val playlist: PlaylistModel) : RemovableTrackList {
        override val removeAction get() = OptionsAction.REMOVE_FROM_PLAYLIST
        override val removalMessage @Composable get() = stringResource(R.string.removal_from_playlist, playlist.title)
    }

    /** A track taken out of it keeps how often it was played, and comes back the next time it plays. */
    data object RecentlyPlayed : RemovableTrackList {
        override val removeAction get() = OptionsAction.REMOVE_FROM_RECENTLY_PLAYED
        override val removalMessage @Composable get() = stringResource(R.string.removal_from_recently_played)
    }

    /** A track taken out of it has its play count reset to zero. */
    data object MostPlayed : RemovableTrackList {
        override val removeAction get() = OptionsAction.REMOVE_FROM_MOST_PLAYED
        override val removalMessage @Composable get() = stringResource(R.string.removal_from_most_played)
    }
}
