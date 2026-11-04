package com.lhacenmed.sona.feature.video.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTimelineTest {

    @Test
    fun `a line shows from its start up to but not at its end`() {
        val timeline = SubtitleTimeline(listOf(TimedCues(1_000L, 2_000L, listOf("a"))))

        assertEquals(emptyList<String>(), timeline.cuesAt(999L))
        assertEquals(listOf("a"), timeline.cuesAt(1_000L))
        assertEquals(listOf("a"), timeline.cuesAt(1_999L))
        assertEquals(emptyList<String>(), timeline.cuesAt(2_000L))
    }

    @Test
    fun `overlapping lines show together, those begun first first`() {
        val timeline = SubtitleTimeline(
            listOf(
                TimedCues(1_500L, 3_000L, listOf("later")),
                TimedCues(1_000L, 2_000L, listOf("earlier")),
            ),
        )

        assertEquals(listOf("earlier", "later"), timeline.cuesAt(1_600L))
    }

    @Test
    fun `a line with no end lasts until the next one, and the last for good`() {
        val timeline = SubtitleTimeline(
            listOf(
                TimedCues(1_000L, null, listOf("a")),
                TimedCues(5_000L, null, listOf("b")),
            ),
        )

        assertEquals(listOf("a"), timeline.cuesAt(4_999L))
        assertEquals(listOf("b"), timeline.cuesAt(5_000L))
        assertEquals(listOf("b"), timeline.cuesAt(Long.MAX_VALUE - 1))
    }

    @Test
    fun `a position before the start, as an offset can make it, shows nothing`() {
        val timeline = SubtitleTimeline(listOf(TimedCues(0L, 1_000L, listOf("a"))))

        assertEquals(emptyList<String>(), timeline.cuesAt(-500L))
    }
}
