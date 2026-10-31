package com.lhacenmed.sona.core.data

import com.lhacenmed.sona.core.model.FolderPicks
import com.lhacenmed.sona.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderPicksTest {

    private fun file(path: String) = Track(
        id = path.hashCode().toLong(), mediaStoreId = 0, title = path, artist = "", artistId = 0, album = "", albumId = 0,
        genre = null, genreId = null, path = path, folderPath = path.substringBeforeLast('/'), durationMs = 0,
        trackNumber = null, discNumber = null, year = null, dateAddedSeconds = 0, coverArtUri = null,
        isManuallyScanned = false, isVideo = false,
    )

    private val a1 = file("/a/1.mp3")
    private val a2 = file("/a/2.mp3")
    private val b1 = file("/a/b/1.mp3")
    private val picks = FolderPicks(listOf(a1, a2, b1))

    @Test
    fun aFolderPickedWhole_isPickedByItsOwnKey() {
        assertEquals(setOf("/a"), picks.toggled(emptySet(), listOf(a1, a2)))
    }

    @Test
    fun everyFileOfAFolderPicked_oneByOne_isTheFolder() {
        val keys = picks.toggled(picks.toggled(emptySet(), listOf(a1)), listOf(a2))
        assertEquals(setOf("/a"), keys)
    }

    @Test
    fun aFileUnpicked_fromAFolderPickedWhole_leavesTheOthersPicked() {
        assertEquals(setOf("/a/2.mp3"), picks.toggled(setOf("/a"), listOf(a1)))
    }

    @Test
    fun aFolderAndAllInside_arePickedTogether_andUnpickedTogether() {
        val keys = picks.toggled(emptySet(), listOf(a1, a2, b1))
        assertEquals(setOf("/a", "/a/b"), keys)
        assertEquals(emptySet<String>(), picks.toggled(keys, listOf(a1, a2, b1)))
    }

    @Test
    fun aPartlyPickedSet_isPickedWhole_notUnpicked() {
        assertEquals(setOf("/a", "/a/b"), picks.toggled(setOf("/a/1.mp3"), listOf(a1, a2, b1)))
    }

    @Test
    fun allBut_turnsWhatIsPicked_intoWhatIsLeftOut_andBack() {
        val picked = setOf("/a/1.mp3")
        val leftOut = picks.allBut(picked)
        assertEquals(setOf("/a/2.mp3", "/a/b"), leftOut)
        assertEquals(picked, picks.allBut(leftOut))
    }

    @Test
    fun keysOfAnotherPrefix_areNotThese() {
        val music = FolderPicks(listOf(a1), keyPrefix = "music:")
        assertTrue(music.isPicked(a1, setOf("music:/a")))
        assertFalse(music.isPicked(a1, setOf("videos:/a")))
        assertTrue(music.owns("music:/a/1.mp3"))
        assertFalse(music.owns("music:/gone"))
    }
}
