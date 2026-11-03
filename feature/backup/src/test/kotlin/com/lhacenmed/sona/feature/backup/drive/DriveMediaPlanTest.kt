package com.lhacenmed.sona.feature.backup.drive

import com.lhacenmed.sona.feature.backup.BackedUpMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveMediaPlanTest {

    private fun wanted(identity: String, path: String) = WantedMedia(
        media = BackedUpMedia(identity, path, sizeBytes = 100, modifiedAt = 1, isVideo = false),
        identity = identity,
        name = path.substringAfterLast('/'),
        pathId = path,
    )

    private fun stored(identity: String, path: String?, isWhole: Boolean = true) = StoredMedia(
        id = "id-$identity",
        name = path?.substringAfterLast('/') ?: identity,
        identity = identity,
        pathId = path,
        isWhole = isWhole,
        isDamaged = !isWhole,
    )

    private fun singleStep(wanted: WantedMedia, vararg stored: StoredMedia) = planDriveMedia(listOf(wanted), stored.toList()).single()

    @Test
    fun anUnchangedFile_isKeptAsItIs() {
        val step = singleStep(wanted("x", "/a/song.mp3"), stored("x", "/a/song.mp3")) as MediaStep.Keep
        assertFalse(step.needsDetails)
    }

    @Test
    fun aRenamedOrMovedFile_isNotUploaded_onlyItsDetailsCatchUp() {
        val step = singleStep(wanted("x", "/b/renamed.mp3"), stored("x", "/a/song.mp3")) as MediaStep.Keep
        assertTrue(step.needsDetails)
    }

    @Test
    fun aFileSentBeforeItsDetailsWereKept_isNotUploaded() {
        val step = singleStep(wanted("x", "/a/song.mp3"), stored("x", path = null)) as MediaStep.Keep
        assertTrue(step.needsDetails)
    }

    @Test
    fun aRetaggedFile_updatesItsOlderVersion_ratherThanAddingACopy() {
        val older = stored("old", "/a/song.mp3")
        assertEquals(older, (singleStep(wanted("new", "/a/song.mp3"), older) as MediaStep.Upload).replacing)
    }

    @Test
    fun aDamagedFile_isSentAgainOverItself() {
        val damaged = stored("x", "/a/song.mp3", isWhole = false)
        assertEquals(damaged, (singleStep(wanted("x", "/a/song.mp3"), damaged) as MediaStep.Upload).replacing)
    }

    @Test
    fun aNewFile_isUploadedAsNew() {
        assertNull((singleStep(wanted("new", "/a/new.mp3"), stored("x", "/a/song.mp3")) as MediaStep.Upload).replacing)
    }

    @Test
    fun aFileFromThatPathStillWanted_isNotUpdatedAway() {
        // song.mp3 was copied to copy.mp3, then song.mp3 retagged: its old bytes are still wanted, at the copy.
        val steps = planDriveMedia(
            listOf(wanted("old", "/a/copy.mp3"), wanted("new", "/a/song.mp3")),
            listOf(stored("old", "/a/song.mp3")),
        )
        assertTrue(steps[0] is MediaStep.Keep)
        assertNull((steps[1] as MediaStep.Upload).replacing)
    }
}
