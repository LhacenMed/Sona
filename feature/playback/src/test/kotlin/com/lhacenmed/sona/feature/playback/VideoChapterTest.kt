package com.lhacenmed.sona.feature.playback

import com.lhacenmed.sona.core.model.VideoChapter
import com.lhacenmed.sona.core.model.indexAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoChapterTest {

    private val chapters = listOf(VideoChapter(0L, "Intro"), VideoChapter(60_000L, "Opening"), VideoChapter(300_000L, "Endgame"))

    @Test
    fun `a moment is in the last chapter to start by then`() {
        assertEquals(0, chapters.indexAt(0L))
        assertEquals(0, chapters.indexAt(59_999L))
        assertEquals(1, chapters.indexAt(60_000L))
        assertEquals(2, chapters.indexAt(900_000L))
    }

    @Test
    fun `a moment before the first chapter is in it, and a video without chapters has none`() {
        assertEquals(0, listOf(VideoChapter(5_000L, "Late start")).indexAt(0L))
        assertNull(emptyList<VideoChapter>().indexAt(10L))
    }
}
