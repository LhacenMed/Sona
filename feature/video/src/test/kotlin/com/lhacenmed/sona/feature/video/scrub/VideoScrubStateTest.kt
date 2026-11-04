package com.lhacenmed.sona.feature.video.scrub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoScrubStateTest {

    private var nowMs = 0L
    private val sought = mutableListOf<Long>()
    private val scrubbing = mutableListOf<Boolean>()
    private val scrub = VideoScrubState(onSeek = { sought += it }, onScrubbingChange = { scrubbing += it }, now = { nowMs })

    @Test
    fun `a scrub tells the player once it begins and once it ends, however often it moves`() {
        scrub.begin(fromMs = 1_000L)
        scrub.begin(fromMs = 9_000L)
        scrub.moveTo(2_000L)
        scrub.moveTo(3_000L)
        scrub.end()
        scrub.end()

        assertEquals(listOf(true, false), scrubbing)
        assertEquals(listOf(2_000L, 3_000L), sought)
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
    fun `a preview wider than the screen still stands at its margin`() {
        assertEquals(8f, previewLeft(thumbX = 50f, previewWidth = 300f, screenWidth = 100f, edgeMargin = 8f), 0f)
    }
}
