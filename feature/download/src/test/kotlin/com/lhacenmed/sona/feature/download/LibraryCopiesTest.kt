package com.lhacenmed.sona.feature.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryCopiesTest {

    @Test
    fun `a video is known by its id in every kind of YouTube link`() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://music.youtube.com/watch?list=x&v=dQw4w9WgXcQ",
        ).forEach { assertEquals(it, "dQw4w9WgXcQ", videoIdOf(it)) }
    }

    @Test
    fun `a link with no video id is known by its address alone`() {
        assertNull(videoIdOf("https://soundcloud.com/artist/track"))
    }
}
