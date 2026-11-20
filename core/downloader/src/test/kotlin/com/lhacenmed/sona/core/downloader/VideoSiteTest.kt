package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.model.siteNameOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSiteTest {

    @Test
    fun `a video's site is named as it names itself, by any of its addresses`() {
        assertEquals("YouTube", siteNameOf("https://youtu.be/NWANo9BRz6Y?si=d3r4elsQWbxLemOl"))
        assertEquals("YouTube", siteNameOf("https://music.youtube.com/watch?v=x"))
        assertEquals("Facebook", siteNameOf("https://www.facebook.com/reel/1656853761974715/"))
        assertEquals("X", siteNameOf("https://twitter.com/a/status/1"))
        assertEquals("Instagram", siteNameOf("https://m.instagram.com/reel/abc/"))
    }

    @Test
    fun `a site not listed is named by its address, and no link by nothing`() {
        assertEquals("example.org", siteNameOf("https://www.example.org/v/1"))
        assertNull(siteNameOf("not a link"))
    }
}
