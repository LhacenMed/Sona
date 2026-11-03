package com.lhacenmed.sona.core.data

import com.lhacenmed.sona.core.database.albumIdOf
import com.lhacenmed.sona.core.database.artistIdOf
import com.lhacenmed.sona.core.database.genreIdOf
import com.lhacenmed.sona.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCollectionsTest {

    private fun track(id: Long, artists: List<String>, album: String, albumArtists: List<String> = artists, genre: String = "Pop", year: Int? = null) = Track(
        id = id,
        mediaStoreId = id,
        title = "Track $id",
        artist = artists.joinToString(", "),
        artistId = artistIdOf(artists.first()),
        album = album,
        albumId = albumIdOf(albumArtists.joinToString(", "), album),
        genre = genre,
        genreId = genreIdOf(genre),
        path = "/m/$id.mp3",
        folderPath = "/m",
        durationMs = 1000,
        trackNumber = null,
        discNumber = null,
        year = year,
        dateAddedSeconds = id,
        coverArtUri = "cover:$id",
        isManuallyScanned = false,
        isVideo = false,
        artistIds = artists.map(::artistIdOf),
        artistNames = artists,
        albumArtist = albumArtists.joinToString(", "),
        albumArtistNames = albumArtists,
        albumCoverArtUri = "album:$album",
    )

    @Test
    fun oneRowForEachArtist_whateverTheCaseItIsSpelledIn() {
        val collections = libraryCollectionsOf(listOf(track(1, listOf("LINKIN PARK"), "A"), track(2, listOf("Linkin Park"), "B"), track(3, listOf("Linkin Park"), "C")))
        val artist = collections.artists.single()
        assertEquals("Linkin Park", artist.name)
        assertEquals(3, artist.trackCount)
        assertEquals(3, artist.albumCount)
    }

    @Test
    fun aCreditNamingSeveral_countsForEach() {
        val collections = libraryCollectionsOf(listOf(track(1, listOf("Sidi", "Ediye"), "A")))
        assertEquals(setOf("Sidi", "Ediye"), collections.artists.map { it.name }.toSet())
        assertTrue(collections.artists.all { it.trackCount == 1 })
    }

    @Test
    fun anArtistCreditedOnlyOnAnAlbum_canStillBeOpened() {
        val collections = libraryCollectionsOf(listOf(track(1, listOf("Sidi"), "A", albumArtists = listOf("Various Artists"))))
        val various = collections.artists.single { it.name == "Various Artists" }
        assertEquals(0, various.trackCount)
        assertEquals(1, various.albumCount)
    }

    @Test
    fun anAlbumIsItsTracks() {
        val tracks = listOf(track(1, listOf("Sidi"), "A", year = 2020), track(2, listOf("Sidi"), "A", year = 2020), track(3, listOf("Sidi"), "A", year = 1999))
        val album = libraryCollectionsOf(tracks).albums.single()
        assertEquals(3, album.trackCount)
        assertEquals(2020, album.year)
        assertEquals(1L, album.dateAddedSeconds)
        assertEquals("album:A", album.coverArtUri)
    }

    @Test
    fun aGenreCountsItsTracksAndArtists() {
        val genre = libraryCollectionsOf(listOf(track(1, listOf("Sidi"), "A"), track(2, listOf("Ediye"), "B"))).genres.single()
        assertEquals(2, genre.trackCount)
        assertEquals(2, genre.artistCount)
    }

    /** Timed once the JVM has warmed up, as a phone's runtime has by the time a library changes - the first run is its loading classes. */
    @Test
    fun aHugeLibrary_isWorkedOutInAMoment() {
        fun tracksOf(count: Long) = (1L..count).map { track(it, listOf("Artist ${it % 2_000}", "Guest ${it % 500}"), "Album ${it % 3_000}") }
        fun millisFor(tracks: List<Track>): Long {
            val startedAt = System.nanoTime()
            libraryCollectionsOf(tracks)
            return (System.nanoTime() - startedAt) / 1_000_000
        }
        val huge = tracksOf(20_000)
        val mine = tracksOf(815)
        println("libraryCollectionsOf: first run ${millisFor(mine)} ms for 815 tracks")
        repeat(10) { millisFor(huge) }
        println("libraryCollectionsOf: warm ${millisFor(mine)} ms for 815 tracks")
        val warm = millisFor(huge)
        println("libraryCollectionsOf: warm $warm ms for 20000 tracks")
        assertTrue("took $warm ms", warm < 500)
    }
}
