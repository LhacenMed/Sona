package com.lhacenmed.sona.feature.playback

import com.lhacenmed.sona.core.model.ReplayHeatmap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayHeatmapTest {

    private val video = requireNotNull(ReplayHeatmap.of(100_000L))

    @Test
    fun `a video watched through once, or twice alike, has nothing to show`() {
        assertNull(video.played(0L, 100_000L).heights())
        assertNull(video.played(0L, 100_000L).played(0L, 100_000L).heights())
    }

    @Test
    fun `a stretch watched again stands at the top, the rest at what it was against it`() {
        val heights = requireNotNull(video.played(0L, 100_000L).played(40_000L, 50_000L).heights())
        assertEquals(1f, heights[45], 0f)
        assertEquals(0.5f, heights[10], 0f)
    }

    @Test
    fun `part of a moment played counts as that part of it`() {
        val heights = requireNotNull(video.played(0L, 100_000L).played(40_000L, 50_000L).played(40_000L, 50_500L).heights())
        assertEquals(1f, heights[45], 0f)
        // Moment 50, from 50 s to 51 s: once through, and half of it once more, against the three times of moment 45.
        assertEquals(1.5f / 3f, heights[50], 1e-6f)
    }

    @Test
    fun `a short scene replayed once stands out in a two-hour film`() {
        val film = requireNotNull(ReplayHeatmap.of(7_200_000L))
        val heights = requireNotNull(film.played(0L, 7_200_000L).played(3_600_000L, 3_610_000L).heights())
        assertEquals(ReplayHeatmap.PointCount, heights.size)
        assertEquals(1f, heights[50], 0f)
        assertEquals(0.5f, heights[10], 0f)
    }

    @Test
    fun `a scene replayed once while the rest is not watched still shows`() {
        val film = requireNotNull(ReplayHeatmap.of(3_600_000L))
        assertNotNull(film.played(600_000L, 610_000L).played(600_000L, 610_000L).heights())
    }

    @Test
    fun `a stretch outside the video, or backwards, adds nothing`() {
        val played = video.played(0L, 100_000L).played(40_000L, 50_000L)
        assertArrayEquals(played.heights(), played.played(120_000L, 130_000L).played(30_000L, 20_000L).heights(), 0f)
    }

    @Test
    fun `it reads back as it was kept`() {
        val played = video.played(0L, 100_000L).played(10_000L, 20_000L)
        val read = requireNotNull(ReplayHeatmap.fromBytes(played.durationMs, played.toBytes()))
        assertArrayEquals(played.heights(), read.heights(), 0f)
        assertNull(ReplayHeatmap.fromBytes(100_000L, ByteArray(3)))
    }
}
