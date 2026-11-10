package com.lhacenmed.sona.core.downloader.sites

import com.lhacenmed.sona.core.downloader.sites.bluesky.BlueskyReader
import com.lhacenmed.sona.core.downloader.sites.facebook.FacebookReader
import com.lhacenmed.sona.core.downloader.sites.instagram.InstagramReader
import com.lhacenmed.sona.core.downloader.sites.linkedin.LinkedInReader
import com.lhacenmed.sona.core.downloader.sites.pinterest.PinterestReader
import com.lhacenmed.sona.core.downloader.sites.reddit.RedditReader
import com.lhacenmed.sona.core.downloader.sites.telegram.TelegramReader
import com.lhacenmed.sona.core.downloader.sites.threads.ThreadsReader
import com.lhacenmed.sona.core.downloader.sites.tiktok.TikTokReader
import com.lhacenmed.sona.core.downloader.sites.x.XReader
import java.net.InetAddress
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteReadersTest {
    @Test
    fun eachSitesPostLinkHasItsReader() {
        val cases = mapOf(
            "https://x.com/someone/status/1790000000000000001" to XReader,
            "https://www.reddit.com/r/synthetic/comments/1abcdef/a_post/" to RedditReader,
            "https://v.redd.it/synthvid0001" to RedditReader,
            "https://www.instagram.com/reel/SynthPost01/" to InstagramReader,
            "https://www.instagram.com/share/reel/SynthShare1/" to InstagramReader,
            "https://www.tiktok.com/@synthetic/video/900000001" to TikTokReader,
            "https://vm.tiktok.com/ZMabc123/" to TikTokReader,
            "https://www.threads.com/@synthetic_user/post/SynthPost01" to ThreadsReader,
            "https://bsky.app/profile/synthetic.test/post/3synthetic001" to BlueskyReader,
            "https://www.pinterest.com/pin/100000000000000001/" to PinterestReader,
            "https://pin.it/AbCd123" to PinterestReader,
            "https://www.facebook.com/reel/1195289147628387" to FacebookReader,
            "https://fb.watch/aBc_12-/" to FacebookReader,
            "https://www.linkedin.com/posts/synthetic-activity-123/" to LinkedInReader,
            "https://lnkd.in/p/AbC123" to LinkedInReader,
            "https://t.me/synthetic_channel/123" to TelegramReader,
        )
        for ((url, reader) in cases) assertSame(url, reader, SiteReaders.readerFor(url))
    }

    @Test
    fun otherLinksHaveNone() {
        for (url in listOf("https://www.youtube.com/watch?v=aaaaaaaaaaa", "https://bit.ly/AbC", "https://t.co/AbC",
            "https://www.facebook.com/photo/?fbid=555", "http://x.com/a/status/12", "https://open.spotify.com/track/0123456789012345678901",
            "not a link", ""))
            assertNull(url, SiteReaders.readerFor(url))
    }

    @Test
    fun onlyPublicAddressesAreReached() {
        for (address in listOf("8.8.8.8", "1.1.1.1", "2606:4700:4700::1111", "::ffff:8.8.8.8"))
            assertTrue(address, PublicAddresses.allows(InetAddress.getByName(address)))
        for (address in listOf("127.0.0.1", "10.0.0.1", "192.168.1.1", "172.16.0.1", "169.254.169.254", "100.64.0.1", "0.0.0.0",
            "224.0.0.1", "::1", "fe80::1", "fc00::1", "::ffff:127.0.0.1", "64:ff9b::a00:1", "2001:db8::1", "2002::1"))
            assertFalse(address, PublicAddresses.allows(InetAddress.getByName(address)))
    }

    @Test
    fun onlyPlainHttpsHostNamesAreUrls() {
        assertNotNull(HttpsUrl.parse("https://www.reddit.com/r/a/comments/1abc/?x=1"))
        for (url in listOf("http://x.com/", "https://x.com:8443/", "https://user@x.com/", "https://127.0.0.1/", "https://[::1]/",
            "https://x.com/a b", "https://x.com/%zz", "https://example.123/", "https://x.com/a\\b", "ftp://x.com/"))
            assertNull(url, HttpsUrl.parse(url))
        val base = checkNotNull(HttpsUrl.parse("https://www.reddit.com/a/b"))
        assertEquals("https://www.reddit.com/c", HttpsUrl.resolve(base, "/c").toString())
        assertNull(HttpsUrl.resolve(base, "http://evil.test/"))
    }

    @Test
    fun infoJsonLeavesOutWhatItCannotNameInFull() {
        fun post(vararg formats: VideoFormat) = VideoPost("id1", null, null, null, 0.0, "https://x.com/i/status/1", "Twitter", formats.toList())
        val json = JSONObject(checkNotNull(videoInfoJson(post(
            VideoFormat("hd", "https://video.twimg.com/a.mp4", 1280, 720, "h264", "aac", headers = mapOf("Referer" to "https://x.com/")),
            VideoFormat("nosize", "https://video.twimg.com/b.mp4", null, null, "h264", "aac"),
        ))))
        assertEquals("video", json.getString("_type"))
        assertEquals("id1", json.getString("title"))
        assertFalse(json.has("duration"))
        val format = json.getJSONArray("formats").getJSONObject(0)
        assertEquals(1, json.getJSONArray("formats").length())
        assertEquals("https", format.getString("protocol"))
        assertEquals(720, format.getInt("height"))
        assertEquals("https://x.com/", format.getJSONObject("http_headers").getString("Referer"))
        assertTrue(format.getJSONObject("http_headers").has("User-Agent"))
        assertNull(videoInfoJson(post(VideoFormat("a", "https://x/1", 1, 1, "h264", "aac"), VideoFormat("a", "https://x/2", 1, 2, "h264", "aac"))))
        assertNull(videoInfoJson(post(VideoFormat("best", "https://x/1", 1, 1, "h264", "aac"))))
        assertNull(videoInfoJson(post(VideoFormat("a+b", "https://x/1", 1, 1, "h264", "aac"))))
        assertNull(videoInfoJson(post()))
    }

    @Test
    fun aTitleIsThePostsFirstLine() {
        assertEquals("First words", firstLine("\n  First   words \nsecond"))
        assertEquals("ab…", firstLine("abc", max = 2))
        assertNull(firstLine(" \n "))
    }
}
