package com.lhacenmed.sona.feature.scanner

import com.lhacenmed.sona.core.database.artistIdOf
import com.lhacenmed.sona.core.data.libraryCollectionsOf
import com.lhacenmed.sona.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryTracksTest {

    private val credits = ArtistCredits(separatesArtists = true)

    private fun track(id: Long, artist: String, album: String, albumArtist: String = "", genre: String? = null) = Track(
        id = id,
        mediaStoreId = id,
        title = "Track $id",
        artist = artist,
        artistId = 0L,
        album = album,
        albumId = 999L,
        genre = genre,
        genreId = null,
        path = "/m/$id.mp3",
        folderPath = "/m",
        durationMs = 1000,
        trackNumber = null,
        discNumber = null,
        year = null,
        dateAddedSeconds = 0,
        coverArtUri = "cover:$id",
        isManuallyScanned = false,
        isVideo = false,
        albumArtist = albumArtist,
    )

    private fun library(vararg tracks: Track) = libraryTracksOf(tracks.toList(), credits)

    @Test
    fun twoSinglesOfOneName_areTwoAlbums() {
        // Your library: MediaStore filed Zedd's "Stay" under The Kid LAROI's album of the same name.
        val (zedd, laroi) = library(track(1, "Zedd & Alessia Cara", "STAY - Single"), track(2, "The Kid LAROI & Justin Bieber", "STAY - Single"))
        assertNotEquals(zedd.albumId, laroi.albumId)
        assertEquals("Zedd & Alessia Cara", zedd.albumArtist)
    }

    @Test
    fun theAlbumArtistTag_keepsAnAlbumTogether() {
        // Your library: The Heist was listed twice.
        val (first, second) = library(track(1, "Macklemore", "The Heist", albumArtist = "Macklemore & Ryan Lewis"), track(2, "Macklemore & Ryan Lewis", "The Heist"))
        assertEquals(first.albumId, second.albumId)
    }

    @Test
    fun anAlbumsCaseVariations_areOneAlbum_withOneCover() {
        val (first, second) = library(track(1, "Lartiste", "CLANDESTINO"), track(2, "Lartiste", "Clandestino"))
        assertEquals(first.albumId, second.albumId)
        assertEquals(first.album, second.album)
        assertEquals(first.albumCoverArtUri, second.albumCoverArtUri)
    }

    @Test
    fun namesDifferingOnlyInSpaces_areOneArtist_asAPersonWritesIt() {
        val tracks = library(track(1, "ImagineDragons", "A"), track(2, "Imagine Dragons", "B"), track(3, "ImagineDragons", "C"))
        assertEquals(setOf(artistIdOf("Imagine Dragons")), tracks.flatMap { it.artistIds }.toSet())
        assertEquals("Imagine Dragons", libraryCollectionsOf(tracks).artists.single().name)
    }

    @Test
    fun aCreditNamingSeveral_makesOneRowForEach_andNoneTwice() {
        val tracks = library(track(1, "1950 SoulClub, 1960 SoulClub, Lewis Hanton", "Classics"), track(2, "Lewis Hanton", "Other"))
        val artists = libraryCollectionsOf(tracks).artists
        assertEquals(setOf("1950 SoulClub", "1960 SoulClub", "Lewis Hanton"), artists.map { it.name }.toSet())
        assertEquals(2, artists.single { it.name == "Lewis Hanton" }.trackCount)
    }

    @Test
    fun tracksIdentifiedTwice_comeOutTheSame() {
        val once = library(track(1, "Sidi & Ediye", "A", genre = "Pop"), track(2, "SIDI", "A", albumArtist = "Sidi & Ediye", genre = "pop"))
        assertEquals(once, libraryTracksOf(once, credits))
    }

    @Test
    fun aGenreSpelledTwoWays_isOne() {
        val tracks = library(track(1, "Sidi", "A", genre = "Pop"), track(2, "Sidi", "B", genre = "POP"), track(3, "Sidi", "C", genre = "Pop"))
        assertEquals("Pop", libraryCollectionsOf(tracks).genres.single().name)
    }
}
