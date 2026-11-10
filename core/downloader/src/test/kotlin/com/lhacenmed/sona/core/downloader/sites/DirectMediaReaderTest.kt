package com.lhacenmed.sona.core.downloader.sites

import com.lhacenmed.sona.core.downloader.DirectTransfer
import com.lhacenmed.sona.core.downloader.mediaInfoOf
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectMediaReaderTest {

    private fun read(url: String) = mediaInfoOf(JSONObject(checkNotNull(DirectMediaReader.read(url, SiteHttp()))))

    @Test
    fun `a link straight to a media file is read from its name alone`() {
        assertTrue(DirectMediaReader.accepts("https://cdn.example.com/media/My%20Song.mp3?token=1"))
        assertFalse(DirectMediaReader.accepts("https://example.com/watch?v=abc"))
        assertFalse(DirectMediaReader.accepts("https://example.com/page.html"))

        val song = read("https://cdn.example.com/media/My%20Song.mp3")
        assertEquals("My Song", song.title)
        assertFalse(song.formats.single().hasVideo)
        assertTrue(song.formats.single().hasAudio)
    }

    @Test
    fun `a video file downloads straight from where it is, without the downloader`() {
        val clip = read("https://cdn.example.com/clip.mp4")
        assertTrue(clip.formats.single().hasVideo)
        assertEquals("file", DirectTransfer().formatFor(clip, DownloadChoices(kind = DownloadKind.VIDEO, captions = false), DownloadOptions())?.id)
        // A WebM is yt-dlp's to download: only an MP4 is taken whole.
        assertNull(DirectTransfer().formatFor(read("https://cdn.example.com/clip.webm"), DownloadChoices(kind = DownloadKind.VIDEO), DownloadOptions()))
    }
}
