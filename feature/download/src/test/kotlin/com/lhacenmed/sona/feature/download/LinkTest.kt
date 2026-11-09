package com.lhacenmed.sona.feature.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkTest {

    @Test
    fun `the link is found among the words a share gives with it`() {
        assertEquals("https://youtu.be/abc?si=x", linkIn("Watch this! https://youtu.be/abc?si=x via the app"))
    }

    @Test
    fun `text with no link has none`() {
        assertNull(linkIn("just words"))
        assertNull(linkIn(null))
    }
}
