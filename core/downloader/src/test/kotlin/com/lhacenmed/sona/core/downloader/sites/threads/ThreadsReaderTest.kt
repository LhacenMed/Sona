package com.lhacenmed.sona.core.downloader.sites.threads

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val Canonical = "https://www.threads.com/@synthetic_user/post/SynthPost01"

class ThreadsReaderTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/sites/threads/$name.html")).readText()
    private fun format(json: String?) = JSONObject(checkNotNull(json)).getJSONArray("formats").getJSONObject(0)
    private fun page(media: String) = "<!DOCTYPE html><script>[\"adp_BarcelonaPostPageTargetQueryRelayPreloader_ab\"," +
        "{\"__bbox\":{\"result\":{\"data\":{\"media\":$media}}}}]</script>"

    @Test
    fun hostsAndPostPathsKeepTheUserAndCode() {
        for (url in listOf("https://www.threads.com/@synthetic_user/post/SynthPost01", "https://threads.com/@synthetic_user/post/SynthPost01/",
            "https://www.threads.net/@synthetic_user/post/SynthPost01/media", "https://threads.net/@synthetic_user/post/SynthPost01?share=1#top",
            "https://WWW.THREADS.COM/@synthetic_user/post/SynthPost01", "https://www.threads.com/%40synthetic_user/post/SynthPost01"))
            assertEquals(url, Canonical, ThreadsPostIdentity.parse(url)?.fetchUrl)
        assertEquals("21664484729113730357", ThreadsPostIdentity.mediaId("SynthPost01"))
        assertEquals("1386527023068903329077", ThreadsPostIdentity.mediaId("SynthVid0001"))
        assertNull(ThreadsPostIdentity.mediaId("Synth+Post"))
        for (url in listOf("https://www.threads.com/@synthetic_user", "https://www.threads.com/@synthetic_user/replies",
            "https://www.threads.net/p/SynthPost01/", "https://www.threads.com/@./post/SynthPost01", "https://www.threads.com/@.user/post/SynthPost01",
            "https://www.threads.com/@user./post/SynthPost01", "https://threads.com.evil.example/@a/post/SynthPost01",
            "https://www.instagram.com/@synthetic_user/post/SynthPost01", "http://www.threads.com/@synthetic_user/post/SynthPost01"))
            assertNull(url, ThreadsPostIdentity.parse(url))
    }

    @Test
    fun aVideoKeepsItsLargestProgressiveFileAndLeavesDashOut() {
        val json = ThreadsReader.parse(fixture("video"), "SynthVid0001", Canonical)
        val format = format(json)
        assertEquals("progressive", format.getString("format_id"))
        assertEquals("https://instagram.fexample.fna.fbcdn.net/o1/v/t2/f2/synthetic_large.mp4", format.getString("url"))
        assertEquals(1280, format.getInt("width"))
        assertEquals(720, format.getInt("height"))
        assertEquals("aac", format.getString("acodec"))
        assertFalse(checkNotNull(json).contains("MPD"))
        val info = JSONObject(json)
        assertTrue(info.getString("thumbnail").endsWith("synthetic_poster.jpg"))
        assertEquals("Synthetic caption", info.getString("title"))
        assertEquals("synthetic_user", info.getString("uploader"))
    }

    @Test
    fun aCarouselIsReadAsItsFirstVideo() {
        val json = checkNotNull(ThreadsReader.parse(fixture("carousel-mixed"), "SynthCarr01", Canonical))
        assertTrue(json.contains("clip_large.mp4"))
        assertFalse(json.contains("clip_later.mp4"))
    }

    @Test
    fun photosAndAnotherPostAreNotRead() {
        assertNull(ThreadsReader.parse(fixture("image"), "SynthPost01", Canonical))
        assertNull(ThreadsReader.parse(fixture("wrong-code"), "SynthPost01", Canonical))
        assertNull(ThreadsReader.parse("<html></html>", "SynthPost01", Canonical))
    }

    @Test
    fun aReportedSizeAndASilentClipStayLiteral() {
        val sized = format(ThreadsReader.parse(page("""{"code":"SynthVid0001","pk":"1386527023068903329077","media_type":2,
            "video_versions":[{"url":"https://scontent.cdninstagram.com/o1/v/t2/sized.mp4","width":1280,"height":720,"filesize":12345}]}"""),
            "SynthVid0001", Canonical))
        assertEquals(12345L, sized.getLong("filesize"))
        val silent = format(ThreadsReader.parse(page("""{"code":"SynthVid0001","pk":"1386527023068903329077","media_type":2,"has_audio":false,
            "original_width":720,"original_height":900,"video_versions":[{"url":"https://scontent.cdninstagram.com/o1/v/t2/silent.mp4"}]}"""),
            "SynthVid0001", Canonical))
        assertEquals(900, silent.getInt("height"))
        assertEquals("none", silent.getString("acodec"))
    }
}
