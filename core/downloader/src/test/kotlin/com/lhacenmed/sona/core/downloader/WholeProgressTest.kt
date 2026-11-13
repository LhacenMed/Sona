package com.lhacenmed.sona.core.downloader

import java.io.File
import org.junit.Assert.assertEquals
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
