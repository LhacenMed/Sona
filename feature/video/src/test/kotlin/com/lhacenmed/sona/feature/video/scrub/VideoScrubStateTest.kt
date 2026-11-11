package com.lhacenmed.sona.feature.video.scrub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoScrubStateTest {

    private var nowMs = 0L
    private val sought = mutableListOf<Long>()
    private var snaps = 0
    private val scrub = VideoScrubState(onSeek = { sought += it }, onSnapped = { snaps++ }, now = { nowMs })

    /** Drags to [positionMs] [afterMs] after the last move, the video at [playingMs]. */
    private fun drag(positionMs: Long, playingMs: Long = 1_000L, afterMs: Long = 10L) {
        nowMs += afterMs
        scrub.dragTo(positionMs, playingMs, snapRadiusMs = 100L, releaseRadiusMs = 200L)
    }

    @Test
    fun `the video is moved once, as the scrub lets go, however often it moved`() {
        scrub.begin(fromMs = 1_000L)
        scrub.begin(fromMs = 9_000L)
        scrub.moveTo(2_000L)
        scrub.moveTo(3_000L)
        assertEquals(emptyList<Long>(), sought)

        scrub.end()
        scrub.end()

        assertEquals(listOf(3_000L), sought)
    }

    @Test
    fun `it keeps where it began, whichever way it goes, and a second begin moves nothing`() {
        scrub.begin(fromMs = 1_000L)
        scrub.moveTo(5_000L)
        scrub.begin(fromMs = 5_000L)

        assertEquals(1_000L, scrub.originMs)
        assertEquals(5_000L, scrub.targetMs)
        assertTrue(scrub.isActive)
    }

    @Test
    fun `the thumb moves freely as it first leaves where the video is`() {
        scrub.begin(fromMs = 1_000L)
        drag(1_050L)

        assertEquals(1_050L, scrub.targetMs)
        assertEquals(0, snaps)
    }

    @Test
    fun `brought back near where the video is, it snaps there however slowly it comes, once`() {
        scrub.begin(fromMs = 1_000L)
        drag(2_000L)
        drag(1_300L, afterMs = 200L)
        drag(1_150L, afterMs = 200L)
        drag(1_090L, afterMs = 200L)
        drag(1_050L, afterMs = 200L)

        assertTrue(scrub.isSnapped)
        assertEquals(1_000L, scrub.targetMs)
        assertEquals(1, snaps)
    }

    @Test
    fun `moved on after resting snapped, it lets go and moves freely until it has left and come back`() {
        scrub.begin(fromMs = 1_000L)
        drag(2_000L)
        drag(1_050L)
        nowMs += 400L
        drag(1_060L, afterMs = 0L)
        assertTrue(scrub.isSnapped)

        drag(1_090L)
        assertFalse(scrub.isSnapped)
        assertEquals(1_090L, scrub.targetMs)
        drag(1_010L)
        assertEquals(1_010L, scrub.targetMs)

        drag(1_300L)
        drag(1_050L)
        assertTrue(scrub.isSnapped)
        assertEquals(2, snaps)
    }

    @Test
    fun `snapped, it holds while moving on without a rest, and through a resting finger's tremor`() {
        scrub.begin(fromMs = 1_000L)
        drag(2_000L)
        drag(1_080L)
        drag(1_050L, afterMs = 100L)
        drag(1_020L, afterMs = 100L)
        drag(1_070L, afterMs = 100L)
        assertTrue(scrub.isSnapped)

        drag(1_080L, afterMs = 400L)
        assertTrue(scrub.isSnapped)
    }

    @Test
    fun `a fast swipe landing just past where the video is snaps there too`() {
        scrub.begin(fromMs = 1_000L)
        drag(2_000L)
        drag(1_500L)
        drag(850L)

        assertTrue(scrub.isSnapped)
        assertEquals(1, snaps)
    }

    @Test
    fun `a swipe going on far beyond where the video is does not snap`() {
        scrub.begin(fromMs = 1_000L)
        drag(2_000L)
        drag(1_500L)
        drag(500L)

        assertFalse(scrub.isSnapped)
        assertEquals(0, snaps)
    }

    @Test
    fun `snapped, it follows the video until pulled out past the release radius`() {
        scrub.begin(fromMs = 1_000L)
        drag(2_000L)
        drag(1_500L)
        drag(1_050L)

        drag(1_150L, playingMs = 1_100L)
        assertEquals(1_100L, scrub.targetMs)
        assertEquals(1_120L, scrub.shownPositionMs(playerMs = 1_120L))

        drag(1_400L, playingMs = 1_100L)
        assertFalse(scrub.isSnapped)
        assertEquals(1_400L, scrub.targetMs)
    }

    @Test
    fun `letting go snapped releases to cancel, the video playing on`() {
        scrub.begin(fromMs = 1_000L)
        drag(2_000L)
        drag(1_500L)
        drag(1_050L)
        scrub.end()

        assertEquals(emptyList<Long>(), sought)
        assertFalse(scrub.isSnapped)
        assertEquals(1_700L, scrub.shownPositionMs(playerMs = 1_700L))
    }

    @Test
    fun `a new scrub has to leave where the video is before it can snap`() {
        scrub.begin(fromMs = 1_000L)
        drag(2_000L)
        scrub.end()

        scrub.begin(fromMs = 2_000L)
        drag(2_050L, playingMs = 2_000L)

        assertEquals(2_050L, scrub.targetMs)
        assertEquals(0, snaps)
    }

    @Test
    fun `the bar follows the finger, whatever the player says, while a scrub is under way`() {
        scrub.begin(fromMs = 1_000L)
        scrub.moveTo(50_000L)

        assertEquals(50_000L, scrub.shownPositionMs(playerMs = 1_000L))
    }

    @Test
    fun `after letting go the bar stays put until the player has caught up, never jumping back`() {
        scrub.begin(fromMs = 1_000L)
        scrub.moveTo(50_000L)
        scrub.end()

        assertFalse(scrub.isActive)
        assertEquals(50_000L, scrub.shownPositionMs(playerMs = 1_000L))
        assertEquals(50_500L, scrub.shownPositionMs(playerMs = 50_500L))
        assertEquals(2_000L, scrub.shownPositionMs(playerMs = 2_000L))
    }

    @Test
    fun `a player that never catches up is followed again after a moment`() {
        scrub.begin(fromMs = 1_000L)
        scrub.moveTo(50_000L)
        scrub.end()

        nowMs = 1_499L
        assertEquals(50_000L, scrub.shownPositionMs(playerMs = 1_000L))
        nowMs = 1_500L
        assertEquals(1_000L, scrub.shownPositionMs(playerMs = 1_000L))
    }

    @Test
    fun `the preview stands over the thumb, kept inside the screen`() {
        assertEquals(415f, previewLeft(thumbX = 500f, previewWidth = 170f, screenWidth = 1000f, edgeMargin = 8f), 0f)
        assertEquals(8f, previewLeft(thumbX = 10f, previewWidth = 170f, screenWidth = 1000f, edgeMargin = 8f), 0f)
        assertEquals(822f, previewLeft(thumbX = 990f, previewWidth = 170f, screenWidth = 1000f, edgeMargin = 8f), 0f)
    }

    @Test
    fun `a scrub called off seeks nowhere, and the finger still down moves nothing until it lifts`() {
        scrub.begin(fromMs = 1_000L)
        scrub.moveTo(5_000L)
        scrub.cancel()
        assertFalse(scrub.isActive)
        assertTrue(scrub.isHeld)

        scrub.begin(fromMs = 0L)
        drag(8_000L)
        scrub.moveTo(9_000L)
        assertFalse(scrub.isActive)
        scrub.end()

        assertEquals(emptyList<Long>(), sought)
        assertFalse(scrub.isHeld)
        assertEquals(2_000L, scrub.shownPositionMs(playerMs = 2_000L))
    }

    @Test
    fun `once lifted, the next scrub goes as any other`() {
        scrub.begin(fromMs = 1_000L)
        scrub.cancel()
        scrub.end()

        scrub.begin(fromMs = 0L)
        scrub.moveTo(4_000L)
        scrub.end()

        assertEquals(listOf(4_000L), sought)
    }

    @Test
    fun `a video gone while the bar settles shows the new one's position at once`() {
        scrub.begin(fromMs = 1_000L)
        scrub.moveTo(5_000L)
        scrub.end()
        scrub.cancel()

        assertEquals(0L, scrub.shownPositionMs(playerMs = 0L))
        assertFalse(scrub.isHeld)
    }

    @Test
    fun `a preview wider than the screen still stands at its margin`() {
        assertEquals(8f, previewLeft(thumbX = 50f, previewWidth = 300f, screenWidth = 100f, edgeMargin = 8f), 0f)
    }
}
