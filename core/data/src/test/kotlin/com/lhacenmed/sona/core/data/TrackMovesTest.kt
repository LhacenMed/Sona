package com.lhacenmed.sona.core.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMovesTest {

    /** Every file's bytes, as a fingerprint, by path - what was read of a gone one, or is read of a found one. */
    private val fingerprints = mutableMapOf<String, String>()
    private val reads = mutableListOf<String>()

    private fun file(id: Long, path: String, fingerprint: String, size: Long = 100, isVideo: Boolean = false) =
        TrackFile(id, path, size, isVideo).also { fingerprints[path] = fingerprint }

    private fun moves(gone: List<TrackFile>, found: List<TrackFile>) = runBlocking {
        movedTracks(gone, found, lastFingerprintOf = { fingerprints[it.path] }, fingerprintOf = { reads += it.path; fingerprints[it.path] })
    }

    @Test
    fun aMovedFile_keepsItsTrack() {
        val moved = moves(gone = listOf(file(1, "/a/song.mp3", "x")), found = listOf(file(2, "/b/song.mp3", "x")))
        assertEquals(mapOf(1L to 2L), moved)
    }

    @Test
    fun aRenamedFile_keepsItsTrack() {
        val moved = moves(gone = listOf(file(1, "/a/song.mp3", "x")), found = listOf(file(2, "/a/Song (live).mp3", "x")))
        assertEquals(mapOf(1L to 2L), moved)
    }

    @Test
    fun otherBytes_areANewTrack() {
        val moved = moves(gone = listOf(file(1, "/a/song.mp3", "x")), found = listOf(file(2, "/b/song.mp3", "y")))
        assertEquals(emptyMap<Long, Long>(), moved)
    }

    @Test
    fun aFileOfAnotherSize_isNeverRead() {
        moves(gone = listOf(file(1, "/a/song.mp3", "x", size = 100)), found = listOf(file(2, "/b/other.mp3", "x", size = 200)))
        assertEquals(emptyList<String>(), reads)
    }

    @Test
    fun aVideo_isNeverASong() {
        val moved = moves(gone = listOf(file(1, "/a/clip", "x")), found = listOf(file(2, "/b/clip", "x", isVideo = true)))
        assertEquals(emptyMap<Long, Long>(), moved)
    }

    @Test
    fun twoCopies_stayTwoTracks_eachWithItsOwnName() {
        val moved = moves(
            gone = listOf(file(1, "/a/one.mp3", "x"), file(2, "/a/two.mp3", "x")),
            found = listOf(file(3, "/b/two.mp3", "x"), file(4, "/b/one.mp3", "x")),
        )
        assertEquals(mapOf(1L to 4L, 2L to 3L), moved)
    }

    @Test
    fun oneGoneFile_foundTwice_isPairedOnce() {
        val moved = moves(gone = listOf(file(1, "/a/song.mp3", "x")), found = listOf(file(2, "/b/copy.mp3", "x"), file(3, "/c/song.mp3", "x")))
        assertEquals(mapOf(1L to 3L), moved)
    }
}
