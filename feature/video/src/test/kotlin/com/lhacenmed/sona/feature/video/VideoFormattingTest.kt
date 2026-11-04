package com.lhacenmed.sona.feature.video

import com.lhacenmed.sona.feature.video.gesture.GestureFeedback
import com.lhacenmed.sona.feature.video.gesture.accumulatedSeekMs
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFormattingTest {

    @Test
    fun `what is left reads with a true minus, and never below nothing`() {
        assertEquals("−1:45:41", formatRemainingTime(6_341_000L))
        assertEquals("−00:00", formatRemainingTime(-5L))
    }

    @Test
    fun `how far a scrub has gone always says which way`() {
        assertEquals("+16:28", formatSeekDelta(988_000L))
        assertEquals("−16:28", formatSeekDelta(-988_000L))
        assertEquals("+00:00", formatSeekDelta(0L))
    }

    @Test
    fun `a double tap's seek reads in seconds`() {
        assertEquals("−10 s", formatSeekSeconds(-10_000L))
        assertEquals("+90 s", formatSeekSeconds(90_000L))
    }

    @Test
    fun `a subtitle offset of nothing has no sign, and any other has one`() {
        assertEquals("0.0 s", formatSubtitleOffset(0L))
        assertEquals("+0.5 s", formatSubtitleOffset(500L))
        assertEquals("−1.2 s", formatSubtitleOffset(-1_200L))
    }

    @Test
    fun `a video's details say what is known of it`() {
        assertEquals("2018 · 720p", videoDetails(2018, 720))
        assertEquals("720p", videoDetails(null, 720))
        assertEquals("2018", videoDetails(2018, 0))
        assertEquals("", videoDetails(null, 0))
    }

    @Test
    fun `double taps the same way add up, and one the other way starts over`() {
        val showing = GestureFeedback.DoubleTapSeek(-10_000L)

        assertEquals(-20_000L, accumulatedSeekMs(showing, -10_000L))
        assertEquals(10_000L, accumulatedSeekMs(showing, 10_000L))
        assertEquals(-10_000L, accumulatedSeekMs(null, -10_000L))
        assertEquals(10_000L, accumulatedSeekMs(GestureFeedback.Volume(0.5f), 10_000L))
    }
}
