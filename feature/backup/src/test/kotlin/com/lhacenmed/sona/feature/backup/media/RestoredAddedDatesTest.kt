package com.lhacenmed.sona.feature.backup.media

import com.lhacenmed.sona.feature.backup.BackedUpMedia
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoredAddedDatesTest {

    private fun media(addedAtSeconds: Long, modifiedAt: Long = 0L) =
        BackedUpMedia("f$addedAtSeconds$modifiedAt", "/m/x", 1L, modifiedAt, isVideo = false, addedAtSeconds = addedAtSeconds)

    @Test
    fun `restored files keep their order, the newest now`() {
        val dates = restoredAddedDates(
            listOf(media(300) to "/c", media(100) to "/a", media(200) to "/b"),
            libraryNewestSeconds = 5_000,
            nowSeconds = 10_000,
        )

        assertEquals(mapOf("/a" to 9_998L, "/b" to 9_999L, "/c" to 10_000L), dates)
    }

    @Test
    fun `restored files stand after every file already here, however late those are`() {
        val dates = restoredAddedDates(listOf(media(1) to "/a", media(2) to "/b"), libraryNewestSeconds = 10_000, nowSeconds = 10_000)

        assertEquals(mapOf("/a" to 10_001L, "/b" to 10_002L), dates)
    }

    @Test
    fun `a backup made before dates were kept is ordered by when its files last changed`() {
        val dates = restoredAddedDates(
            listOf(media(0, modifiedAt = 9_000) to "/late", media(0, modifiedAt = 1_000) to "/early"),
            libraryNewestSeconds = 0,
            nowSeconds = 100,
        )

        assertEquals(mapOf("/early" to 99L, "/late" to 100L), dates)
    }
}
