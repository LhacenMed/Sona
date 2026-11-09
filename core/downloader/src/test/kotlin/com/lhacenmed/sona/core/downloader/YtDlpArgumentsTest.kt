package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.model.AudioFormat
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpArgumentsTest {

    private fun List<String>.valueOf(option: String): String? = getOrNull(indexOf(option) + 1)?.takeIf { option in this }

    @Test
    fun `a video is kept at its quality, as mp4, with its captions and chapters`() {
        val arguments = DownloadChoices(kind = DownloadKind.VIDEO, videoQuality = VideoQuality.P720, captions = true).toArguments("fr")
        assertEquals("res:720", arguments.valueOf("-S"))
        assertEquals("mp4", arguments.valueOf("--merge-output-format"))
        assertEquals("fr.*,en.*,-live_chat", arguments.valueOf("--sub-langs"))
        assertEquals("srt", arguments.valueOf("--convert-subs"))
        assertTrue("--embed-subs" in arguments)
        assertTrue("--embed-chapters" in arguments)
        assertFalse("-x" in arguments)
    }

    @Test
    fun `the best video has no limit, and no captions unless asked`() {
        val arguments = DownloadChoices(kind = DownloadKind.VIDEO, videoQuality = VideoQuality.BEST, captions = false).toArguments("en")
        assertNull(arguments.valueOf("-S"))
        assertFalse("--write-subs" in arguments)
    }

    @Test
    fun `audio is extracted with its cover, converted only when asked`() {
        val original = DownloadChoices(kind = DownloadKind.AUDIO, audioFormat = AudioFormat.ORIGINAL).toArguments("en")
        assertTrue("-x" in original)
        assertTrue("--embed-thumbnail" in original)
        assertNull(original.valueOf("--audio-format"))
        assertFalse("--write-subs" in original)

        val mp3 = DownloadChoices(kind = DownloadKind.AUDIO, audioFormat = AudioFormat.MP3).toArguments("en")
        assertEquals("mp3", mp3.valueOf("--audio-format"))
    }

    @Test
    fun `sponsors are cut only when asked`() {
        assertFalse("--sponsorblock-remove" in DownloadChoices(skipSponsors = false).toArguments("en"))
        assertTrue("--sponsorblock-remove" in DownloadChoices(skipSponsors = true).toArguments("en"))
    }

    @Test
    fun `an item line names the video, and where it stands in a playlist`() {
        assertEquals(DownloadEvent.Item("Song title", 2, 5), parseLine("${YtDlpLines.ITEM} 2 5 Song title"))
        assertEquals(DownloadEvent.Item("One video", null, null), parseLine("${YtDlpLines.ITEM}   One video"))
    }

    @Test
    fun `a progress line is how far, or nothing known while the size is not`() {
        assertEquals(DownloadEvent.Progress(0.25f), parseLine("${YtDlpLines.PROGRESS} 250 1000"))
        assertEquals(DownloadEvent.Progress(null), parseLine("${YtDlpLines.PROGRESS} 250 NA"))
    }

    @Test
    fun `anything else yt-dlp writes says nothing`() {
        assertNull(parseLine("[download] Destination: video.mp4"))
    }
}
