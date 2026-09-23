package com.lhacenmed.sona.core.data.playlist

import com.lhacenmed.sona.core.database.dao.PlaylistWithCount
import com.lhacenmed.sona.core.database.entity.PlaylistCoverSource
import com.lhacenmed.sona.core.model.PlaylistCover
import com.lhacenmed.sona.core.model.Track

/**
 * What a playlist whose cover is this draws: the one image it names, or [stackedCoverArtUris] wherever
 * the choice has nothing to show - an empty playlist's first or last track, a track without artwork, or
 * a chosen track no longer in the library.
 *
 * The one rule for the playlist everywhere it is drawn and for the editor's preview of it, so what the
 * editor shows is what saving it gives. [sortedTracks] - the playlist's tracks in its current sort - is
 * read only for the first and last track, and [chosenTrackCoverArtUri] only for a chosen track.
 */
fun PlaylistCover.coverArtUris(
    stackedCoverArtUris: List<String>,
    sortedTracks: List<Track>,
    chosenTrackCoverArtUri: String?,
): List<String> {
    val chosenCoverArtUri = when (this) {
        PlaylistCover.Stacked -> null
        PlaylistCover.FirstTrack -> sortedTracks.firstOrNull()?.coverArtUri
        PlaylistCover.LastTrack -> sortedTracks.lastOrNull()?.coverArtUri
        is PlaylistCover.OfTrack -> chosenTrackCoverArtUri
        is PlaylistCover.Image -> uri
    }
    return chosenCoverArtUri?.let(::listOf) ?: stackedCoverArtUris
}

/** The cover a stored row names. A source missing the value it needs reads as stacked. */
internal fun PlaylistWithCount.cover(): PlaylistCover = when (coverSource) {
    PlaylistCoverSource.STACKED -> PlaylistCover.Stacked
    PlaylistCoverSource.FIRST_TRACK -> PlaylistCover.FirstTrack
    PlaylistCoverSource.LAST_TRACK -> PlaylistCover.LastTrack
    PlaylistCoverSource.TRACK -> coverTrackId?.let(PlaylistCover::OfTrack) ?: PlaylistCover.Stacked
    PlaylistCoverSource.IMAGE -> coverImageUri?.let(PlaylistCover::Image) ?: PlaylistCover.Stacked
}

internal val PlaylistCover.source: PlaylistCoverSource
    get() = when (this) {
        PlaylistCover.Stacked -> PlaylistCoverSource.STACKED
        PlaylistCover.FirstTrack -> PlaylistCoverSource.FIRST_TRACK
        PlaylistCover.LastTrack -> PlaylistCoverSource.LAST_TRACK
        is PlaylistCover.OfTrack -> PlaylistCoverSource.TRACK
        is PlaylistCover.Image -> PlaylistCoverSource.IMAGE
    }
