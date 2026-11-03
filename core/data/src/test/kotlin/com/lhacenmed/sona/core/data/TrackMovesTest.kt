package com.lhacenmed.sona.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMovesTest {

    private fun file(
        id: Long,
        path: String,
        size: Long = 100,
        durationMs: Long = 60_000,
        isVideo: Boolean = false,
        title: String = "Title $id",
        artist: String = "Artist $id",
    ) = TrackFile(id, path, size, durationMs, isVideo, title, artist)

    private val none = emptyMap<String, TrackFile>()

    @Test
    fun aMovedFile_keepsItsTrack() {
        val gone = file(1, "/a/song.mp3")
        assertEquals(mapOf("/b/song.mp3" to gone), movedTracks(listOf(gone), listOf(file(2, "/b/song.mp3"))))
    }

    @Test
    fun aRenamedFile_keepsItsTrack() {
        val gone = file(1, "/a/song.mp3")
        assertEquals(mapOf("/a/Song (live).mp3" to gone), movedTracks(listOf(gone), listOf(file(2, "/a/Song (live).mp3"))))
    }

    @Test
    fun aMovedVideo_keepsItsTrack() {
        val gone = file(1, "/a/film.mp4", isVideo = true)
        assertEquals(mapOf("/b/film.mp4" to gone), movedTracks(listOf(gone), listOf(file(2, "/b/film.mp4", isVideo = true))))
    }

    @Test
    fun aFileMovedAndRetagged_keepsItsTrack_byItsNameAndLength() {
        val gone = file(1, "/a/song.mp3")
        assertEquals(mapOf("/b/song.mp3" to gone), movedTracks(listOf(gone), listOf(file(2, "/b/song.mp3", size = 150))))
    }

    @Test
    fun aFileRenamedWithANewCover_keepsItsTrack_byItsTagsAndLength() {
        val gone = file(1, "/a/song.mp3", title = "Song", artist = "Sidi")
        val other = file(3, "/b/other.mp3", size = 300, title = "Other", artist = "Ediye")
        val moved = movedTracks(listOf(gone), listOf(other, file(2, "/b/renamed.mp3", size = 150, title = "Song", artist = "Sidi")))
        assertEquals(mapOf("/b/renamed.mp3" to gone), moved)
    }

    @Test
    fun aFileRenamedAndRetagged_keepsItsTrack_byALengthOnlyTheyShare() {
        val gone = file(1, "/a/song.mp3")
        assertEquals(mapOf("/b/renamed.mp3" to gone), movedTracks(listOf(gone), listOf(file(2, "/b/renamed.mp3", size = 150))))
    }

    @Test
    fun aLengthSharedWithAnotherFile_tellsNothing() {
        val found = listOf(file(2, "/b/one.mp3", size = 150), file(3, "/b/two.mp3", size = 160))
        assertEquals(none, movedTracks(listOf(file(1, "/a/song.mp3")), found))
    }

    @Test
    fun aFileOfAnotherLength_isANewTrack() {
        assertEquals(none, movedTracks(listOf(file(1, "/a/song.mp3")), listOf(file(2, "/b/song.mp3", durationMs = 90_000))))
    }

    @Test
    fun aVideo_isNeverASong() {
        assertEquals(none, movedTracks(listOf(file(1, "/a/song.mp4")), listOf(file(2, "/b/song.mp4", isVideo = true))))
    }

    @Test
    fun filesOfUnknownLength_areNeverPaired() {
        assertEquals(none, movedTracks(listOf(file(1, "/a/song.mp3", durationMs = 0)), listOf(file(2, "/b/song.mp3", durationMs = 0))))
    }

    @Test
    fun twoCopies_stayTwoTracks_eachWithItsOwnName() {
        val first = file(1, "/a/first.mp3")
        val second = file(2, "/a/second.mp3")
        val moved = movedTracks(listOf(first, second), listOf(file(3, "/b/second.mp3"), file(4, "/b/first.mp3")))
        assertEquals(mapOf("/b/first.mp3" to first, "/b/second.mp3" to second), moved)
    }

    @Test
    fun oneGoneFile_foundTwice_isPairedOnce() {
        val gone = file(1, "/a/song.mp3")
        val moved = movedTracks(listOf(gone), listOf(file(2, "/b/copy.mp3"), file(3, "/b/song.mp3")))
        assertEquals(mapOf("/b/song.mp3" to gone), moved)
    }

    @Test
    fun aCopyLeftBehind_isTheGoneTrack() {
        val gone = file(1, "/sd/song.mp3")
        assertEquals(mapOf("/internal/song.mp3" to gone), copiesOfGoneTracks(listOf(gone), listOf(file(2, "/internal/song.mp3"))))
    }

    @Test
    fun aRetaggedFile_isNeverTakenForACopy() {
        assertEquals(none, copiesOfGoneTracks(listOf(file(1, "/sd/song.mp3")), listOf(file(2, "/internal/song.mp3", size = 150))))
    }

    @Test
    fun oneCopy_standsForOneGoneTrack() {
        val first = file(1, "/sd/a.mp3")
        val second = file(2, "/sd/b.mp3")
        assertEquals(mapOf("/internal/a.mp3" to first), copiesOfGoneTracks(listOf(first, second), listOf(file(3, "/internal/a.mp3"))))
    }
}
