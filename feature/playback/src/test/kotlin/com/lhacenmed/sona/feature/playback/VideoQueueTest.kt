package com.lhacenmed.sona.feature.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoQueueTest {

    private val isVideo: (String) -> Boolean = { it.startsWith("v") }

    @Test
    fun `a list of videos is watched as it is`() {
        assertEquals(listOf("v1", "v2", "v3") to 1, videosFrom(listOf("v1", "v2", "v3"), 1, isVideo))
    }

    @Test
    fun `music in the list stays out, the start staying on the same video`() {
        assertEquals(listOf("v1", "v2") to 1, videosFrom(listOf("m1", "v1", "m2", "v2", "m3"), 3, isVideo))
    }

    @Test
    fun `the first video of a list starting with music is at the queue's start`() {
        assertEquals(listOf("v1") to 0, videosFrom(listOf("m1", "m2", "v1"), 2, isVideo))
    }
}
