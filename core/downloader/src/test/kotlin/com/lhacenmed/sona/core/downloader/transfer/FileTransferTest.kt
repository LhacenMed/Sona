package com.lhacenmed.sona.core.downloader.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTransferTest {

    @Test
    fun `a file is named as its Content-Disposition says, its UTF-8 name first`() {
        assertEquals("Episode 5.mp4", fileNameFromContentDisposition("""attachment; filename="Episode 5.mp4""""))
        assertEquals("épisode 5.mp4", fileNameFromContentDisposition("""attachment; filename="x.mp4"; filename*=UTF-8''%C3%A9pisode%205.mp4"""))
        assertEquals("a+b.zip", fileNameFromContentDisposition("attachment; filename*=UTF-8''a+b.zip"))
        assertEquals("résumé.pdf", fileNameFromContentDisposition("attachment; filename=\"=?UTF-8?B?csOpc3Vtw6kucGRm?=\""))
        assertNull(fileNameFromContentDisposition("inline"))
    }

    @Test
    fun `a Content-Range is read with or without its unit, its size unsaid as a star`() {
        assertEquals(ContentRange(start = 0, total = 1_000), contentRangeOf("bytes 0-255/1000"))
        assertEquals(ContentRange(start = 256, total = 1_000), contentRangeOf("256-511/1000"))
        assertEquals(ContentRange(start = 0, total = null), contentRangeOf("bytes 0-255/*"))
        assertNull(contentRangeOf("bytes 0-255"))
        assertNull(contentRangeOf(null))
    }

    @Test
    fun `a media file is known by how it begins`() {
        assertEquals("video/mp4", mediaTypeOf(bytes(0, 0, 0, 0x20) + "ftypisom".toByteArray()))
        assertEquals("audio/mp4", mediaTypeOf(bytes(0, 0, 0, 0x20) + "ftypM4A ".toByteArray()))
        assertEquals("video/x-matroska", mediaTypeOf(bytes(0x1A, 0x45, 0xDF, 0xA3) + "matroska".toByteArray()))
        assertEquals("video/webm", mediaTypeOf(bytes(0x1A, 0x45, 0xDF, 0xA3) + "webm".toByteArray()))
        assertEquals("audio/mpeg", mediaTypeOf("ID3".toByteArray()))
        assertEquals("audio/x-wav", mediaTypeOf("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray(Charsets.ISO_8859_1)))
        assertNull(mediaTypeOf("PK\u0003\u0004".toByteArray()))
    }

    @Test
    fun `a page, a feed or a stream's playlist is yt-dlp's, whatever it is served as`() {
        assertTrue(beginsMarkupOrPlaylist("<!DOCTYPE html><html>".toByteArray()))
        assertTrue(beginsMarkupOrPlaylist("\uFEFF  <?xml version=\"1.0\"?><MPD>".toByteArray()))
        assertTrue(beginsMarkupOrPlaylist("#EXTM3U\n#EXT-X-VERSION:3".toByteArray()))
        assertFalse(beginsMarkupOrPlaylist("PK\u0003\u0004".toByteArray()))
        assertFalse(beginsMarkupOrPlaylist(ByteArray(0)))
    }

    @Test
    fun `a part let go by a connection that failed is another's to finish, and recorded as it stands`() {
        val parts = FileParts(listOf(FilePart(0, 99)))
        val part = checkNotNull(parts.next(done = null))
        assertNull(parts.next(done = null))
        parts.release(part)
        assertEquals(part, parts.next(done = null))
        assertFalse(parts.isComplete)
        assertEquals(listOf(0L, 99L, 0L), parts.snapshot().single().let { listOf(it.from, it.to, it.current) })
    }

    @Test
    fun `a rate is read as yt-dlp reads it, in 1024s`() {
        assertEquals(500L * 1024, bytesPerSecondOf("500K"))
        assertEquals((4.2 * 1024 * 1024).toLong(), bytesPerSecondOf("4.2M"))
        assertEquals(2048L, bytesPerSecondOf("2048"))
        assertEquals(1024L * 1024 * 1024, bytesPerSecondOf("1GiB"))
        assertNull(bytesPerSecondOf(""))
        assertNull(bytesPerSecondOf("fast"))
    }

    @Test
    fun `a file is split into near even parts, none smaller than the least`() {
        assertEquals(listOf(0L..3L, 4L..6L, 7L..9L), splitToRange(size = 10, minPartBytes = 1, maxParts = 3))
        assertEquals(listOf(0L..9L), splitToRange(size = 10, minPartBytes = 10, maxParts = 8))
        val parts = splitToRange(size = 100 * MinPartBytes + 7, minPartBytes = MinPartBytes, maxParts = ConnectionsPerDownload)
        assertEquals(ConnectionsPerDownload, parts.size)
        assertEquals(100 * MinPartBytes + 6, parts.last().last)
    }

    @Test
    fun `a connection free takes a part no one fetches, then half of the largest - never what is held`() {
        val size = 8 * MinPartBytes
        val parts = FileParts(listOf(FilePart(0, size - 1)))
        val first = checkNotNull(parts.next(done = null))
        val held = parts.claim(first, 64 * 1024)
        assertTrue(held >= 64 * 1024)
        val second = checkNotNull(parts.next(done = null))
        // The split begins past all the first holds, and the two meet with nothing between or doubled.
        assertTrue(second.from > first.claimedTo)
        assertEquals(first.to + 1, second.from)
        assertEquals(size - 1, second.to)
    }

    @Test
    fun `a part too small to be worth a connection is not split`() {
        val parts = FileParts(listOf(FilePart(0, MinPartBytes)))
        parts.next(done = null)
        assertNull(parts.next(done = null))
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
