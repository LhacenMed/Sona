package com.lhacenmed.sona.core.downloader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WholeProgressTest {

    private fun part(name: String) = DownloadEvent.Part(File("$name.part"), File(name))

    private fun WholeProgress.progress(done: Long, total: Long) = of(DownloadEvent.Progress(done, total, null)) as DownloadEvent.Progress

    @Test
    fun `a video's sound and picture fill one bar, never starting over`() {
        val whole = WholeProgress()
        whole.read("${YtDlpLines.SIZE} 100 900 NA")
        whole.of(part("a.f251.webm"))
        assertEquals(0.05f, whole.progress(50, 100).fraction)
        assertEquals(0.1f, whole.progress(100, 100).fraction)
        whole.of(part("a.f399.mp4"))
        assertEquals(0.1f, whole.progress(0, 900).fraction)
        assertEquals(1f, whole.progress(900, 900).fraction)
    }

    @Test
    fun `resumed, a sound fetched whole before still counts, so the bar carries on from where it was`() {
        val sound = File.createTempFile("sound", ".f251.webm").apply { writeBytes(ByteArray(100)) }
        try {
            val whole = WholeProgress()
            whole.read("${YtDlpLines.SIZE} 100 900 NA")
            whole.of(DownloadEvent.Part(File(sound.path + ".part"), sound))
            // What yt-dlp says of it as it passes it by: its size alone.
            assertNull(whole.of(DownloadEvent.Progress(null, 100, null)))
            whole.of(part("a.f399.mp4"))
            assertEquals(0.55f, whole.progress(450, 900).fraction)
        } finally {
            sound.delete()
        }
    }

    @Test
    fun `captions fetched before the streams are no part of how far the video is`() {
        val whole = WholeProgress()
        whole.read("[download] Destination: a.en.vtt")
        assertNull(whole.of(DownloadEvent.Progress(1024, 1024, null)))
        whole.read("${YtDlpLines.SIZE} 100 900 NA")
        whole.read("[download] Destination: a.f251.webm")
        whole.of(part("a.f251.webm"))
        assertEquals(0.05f, whole.progress(50, 100).fraction)
    }

    @Test
    fun `a file larger than expected grows the whole rather than overflowing it`() {
        val whole = WholeProgress()
        whole.read("${YtDlpLines.SIZE} NA NA 100")
        assertEquals(0.5f, whole.progress(100, 200).fraction)
    }

    @Test
    fun `with nothing expected, each file is the whole`() {
        val whole = WholeProgress()
        assertEquals(0.25f, whole.progress(25, 100).fraction)
    }
}
