package com.lhacenmed.sona.core.data

import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.database.artistIdOf
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track

/** The library's albums, artists and genres, unsorted. */
class LibraryCollections(val albums: List<Album>, val artists: List<Artist>, val genres: List<Genre>)

/**
 * The albums, artists and genres [tracks] make up, each followed from its tracks whatever changed: this is all they are.
 * Kept nowhere, none can ever list what no track says or disagree with one - no album stale after a retag, no artist left
 * from before a rule changed - and each is exactly one row, however many tracks name it.
 */
fun libraryCollectionsOf(tracks: List<Track>): LibraryCollections {
    // Each name is worked out once, however many tracks and albums name it: telling names apart is what costs here.
    val artistIds = HashMap<String, Long>()
    fun artistIdNamed(name: String) = artistIds.getOrPut(name) { artistIdOf(name) }

    val albumsByArtist = HashMap<Long, MutableList<Album>>()
    val spellingsByArtist = HashMap<Long, MutableList<String>>()
    val albums = tracks.groupBy { it.albumId }.map { (albumId, albumTracks) ->
        val albumArtists = albumTracks.first().albumArtistNames
        val albumArtistIds = albumArtists.map(::artistIdNamed)
        albumOf(albumId, albumTracks, albumArtistIds).also { album ->
            albumArtists.forEachIndexed { index, name ->
                albumsByArtist.getOrPut(albumArtistIds[index]) { mutableListOf() } += album
                spellingsByArtist.getOrPut(albumArtistIds[index]) { mutableListOf() } += name
            }
        }
    }

    val tracksByArtist = HashMap<Long, MutableList<Track>>()
    for (track in tracks) {
        track.artistIds.forEachIndexed { index, artistId ->
            tracksByArtist.getOrPut(artistId) { mutableListOf() } += track
            track.artistNames.getOrNull(index)?.let { spellingsByArtist.getOrPut(artistId) { mutableListOf() } += it }
        }
    }
    val artists = spellingsByArtist.map { (artistId, spellings) ->
        val artistTracks = tracksByArtist[artistId].orEmpty()
        val artistAlbums = albumsByArtist[artistId].orEmpty()
        Artist(
            id = artistId,
            name = commonestSpelling(spellings),
            trackCount = artistTracks.size,
            albumCount = artistAlbums.size,
            // Auxio falls back to the artist's albums only when none of its tracks has a cover.
            coverArtUris = rankedCoverArtUris(artistTracks.map(Track::collageCover)).ifEmpty { rankedCoverArtUris(artistAlbums.map { it.coverArtUri }) },
        )
    }

    val genres = tracks.filter { it.genreId != null && it.genre != null }.groupBy { it.genreId!! }.map { (genreId, genreTracks) ->
        Genre(
            id = genreId,
            name = genreTracks.first().genre!!,
            trackCount = genreTracks.size,
            artistCount = genreTracks.flatMapTo(HashSet()) { it.artistIds }.size,
            coverArtUris = rankedCoverArtUris(genreTracks.map(Track::collageCover)),
        )
    }
    return LibraryCollections(albums, artists, genres)
}

private fun albumOf(albumId: Long, tracks: List<Track>, artistIds: List<Long>): Album {
    val track = tracks.first()
    return Album(
        id = albumId,
        title = track.album,
        artistId = artistIds.firstOrNull() ?: 0L,
        artistName = track.albumArtist,
        coverArtUri = track.albumCoverArtUri ?: track.coverArtUri,
        year = commonestYear(tracks),
        trackCount = tracks.size,
        dateAddedSeconds = tracks.minOf { it.dateAddedSeconds },
        artistIds = artistIds,
    )
}

/**
 * The cover a track stands for in a collection's collage: its album's. Every track has its own cover, so ten tracks of one
 * album would otherwise fill the collage with the one picture ten times; its album's counts it once.
 */
private val Track.collageCover: String? get() = albumCoverArtUri ?: coverArtUri

/** The year most of [tracks] give, the earliest of a tie, or null where none gives one. */
private fun commonestYear(tracks: List<Track>): Int? =
    tracks.mapNotNull { it.year }.groupingBy { it }.eachCount().entries
        .minWithOrNull(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        ?.key

/**
 * The spelling most of [spellings] use - Auxio melds a cluster into its most popular variant. A tie goes to the first in
 * order, so every time settles on the same one.
 */
fun commonestSpelling(spellings: List<String>): String =
    spellings.groupingBy { it }.eachCount().entries
        .minWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .key
