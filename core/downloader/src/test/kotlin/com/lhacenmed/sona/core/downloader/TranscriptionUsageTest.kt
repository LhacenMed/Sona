package com.lhacenmed.sona.core.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptionUsageTest {

    @Test
    fun groqDurationsAreReadInMilliseconds() {
        assertEquals(179_560L, durationMsOf("2m59.56s"))
        assertEquals(3_720_000L, durationMsOf("1h2m"))
        assertEquals(7_660L, durationMsOf("7.66s"))
        assertEquals(120L, durationMsOf("120ms"))
        assertNull(durationMsOf("soon"))
    }

    @Test
    fun aDayCountedAgainIsWhole() {
        val usage = ServiceUsage.DailyRequests(left = 3, limit = 2_000, resetsAt = 1_000)
        assertEquals(3L, usage.at(now = 999).left)
        assertEquals(2_000L, usage.at(now = 1_000).left)
    }
}
