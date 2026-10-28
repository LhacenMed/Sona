package com.lhacenmed.sona.feature.library.options

import com.lhacenmed.sona.core.model.Playlist as PlaylistModel

/**
 * A list tracks can be taken out of, their files untouched: a playlist, or one of the listening histories. What a
 * track's options sheet and a list's selection bar offer to remove from, and what the dialog asking first says.
 */
sealed interface RemovableTrackList {
    /** What removing from it is offered as. */
    val removeAction: OptionsAction

    /** What follows from removing from it, as the dialog asking first says. */
    val removalMessage: String

    data class Playlist(val playlist: PlaylistModel) : RemovableTrackList {
        override val removeAction get() = OptionsAction.REMOVE_FROM_PLAYLIST
        override val removalMessage get() = "From ${playlist.name}. The files themselves are not deleted."
    }

    /** A track taken out of it keeps how often it was played, and comes back the next time it plays. */
    data object RecentlyPlayed : RemovableTrackList {
        override val removeAction get() = OptionsAction.REMOVE_FROM_RECENTLY_PLAYED
        override val removalMessage get() = "From Recently played, until they play again. The files themselves are not deleted."
    }

    /** A track taken out of it has its play count reset to zero. */
    data object MostPlayed : RemovableTrackList {
        override val removeAction get() = OptionsAction.REMOVE_FROM_MOST_PLAYED
        override val removalMessage get() = "Their play counts start again from zero. The files themselves are not deleted."
    }
}
