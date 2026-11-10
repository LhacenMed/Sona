package com.lhacenmed.sona.core.downloader.sites.instagram

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val Code = "SynthPost01"
private const val Post = "https://www.instagram.com/p/$Code/"

/** A logged-out post page's Relay block, as served, holding [media] as the post's polaris media. */
private fun page(media: String, gating: String = "null", code: String = Code) = """<html><body>
    <script type="application/json" data-sjs>{"require":[["ScheduledServerJS","handle",null,[{"__bbox":{"require":[["RelayPrefetchedStreamCache","next",[],
    ["adp_PolarisPostRootQueryRelayPreloader_1",{"__bbox":{"result":{"data":{"xig_polaris_media":{"code":"$code","gating_ruling":$gating,
    "if_not_gated_logged_out":$media}}}}}]]]}}]]]}</script></body></html>"""

private const val Video = """{"code":"$Code","media_type":2,"has_audio":true,"video_duration":12.5,
    "caption":{"text":"Synthetic caption\nmore"},"user":{"username":"synthetic_user","full_name":"Synthetic User"},
    "image_versions2":{"candidates":[{"url":"https://scontent.cdninstagram.com/v/t51.2885-15/poster.jpg","width":720,"height":1280}]},
    "video_versions":[{"type":101,"url":"https://instagram.fexample-1.fna.fbcdn.net/o1/v/t16/f2/m86/large.mp4?efg=1","width":720,"height":1280},
      {"type":102,"url":"https://instagram.fexample-1.fna.fbcdn.net/o1/v/t16/f2/m86/small.mp4?efg=1","width":480,"height":854},
      {"type":103,"url":"https://instagram.fexample-1.fna.fbcdn.net/o1/v/t16/f2/m86/small.mp4?efg=1","width":480,"height":854}]}"""

class InstagramReaderTest {
    @Test
    fun postReelAndTvLinksReadThePostPage() {
        for (url in listOf(Post, "https://instagram.com/p/$Code", "https://www.instagram.com/p/$Code/?igsh=Synthetic",
            "https://m.instagram.com/p/$Code/", "https://WWW.Instagram.com/p/$Code/", "https://www.instagram.com/reel/$Code/",
            "https://www.instagram.com/reels/$Code/", "https://www.instagram.com/tv/$Code/", "https://instagr.am/synthetic.user/p/$Code/"))
            assertEquals(url, Post, InstagramPostIdentity.parse(url)?.fetchUrl)
        for (url in listOf("https://www.instagram.com/", "https://www.instagram.com/synthetic_user/", "https://www.instagram.com/stories/synthetic_user/123/",
            "https://www.instagram.com/p/abc/", "https://www.instagram.com/p/Synth.Post/", "http://www.instagram.com/p/$Code/",
            "https://instagram.com.evil.example/p/$Code/", "https://www.instagram.com/share/reel/SynthShare1/",
            "https://www.instagram.com/stories/p/$Code/", "https://www.instagram.com/p/reel/$Code/", "https://www.instagram.com/p/${"A".repeat(65)}/"))
            assertNull(url, InstagramPostIdentity.parse(url))
        assertTrue(InstagramReader.accepts("https://www.instagram.com/share/reel/SynthShare1/"))
    }

    @Test
    fun aVideoPostsVersionsBecomeFormats() {
        val info = JSONObject(checkNotNull(InstagramReader.parse(page(Video), Code, Post)))
        val formats = info.getJSONArray("formats")
        assertEquals(2, formats.length())
        assertEquals(1280, formats.getJSONObject(0).getInt("height"))
        assertEquals("aac", formats.getJSONObject(0).getString("acodec"))
        assertEquals("Synthetic caption", info.getString("title"))
        assertEquals("Synthetic User", info.getString("uploader"))
        assertEquals(12.5, info.getDouble("duration"), 0.0)
        assertEquals("Instagram", info.getString("extractor_key"))
    }

    @Test
    fun photosCarouselsGatedPostsAndOtherPostsAreNotRead() {
        assertNull(InstagramReader.parse(page(Video.replace("\"media_type\":2", "\"media_type\":1")), Code, Post))
        assertNull(InstagramReader.parse(page(Video.replace("\"media_type\":2", "\"media_type\":8")), Code, Post))
        assertNull(InstagramReader.parse(page(Video, gating = "{\"x\":1}"), Code, Post))
        assertNull(InstagramReader.parse(page("null"), Code, Post))
        assertNull(InstagramReader.parse(page(Video, code = "OtherPost1"), Code, Post))
        assertNull(InstagramReader.parse(page(Video.replace("instagram.fexample-1.fna.fbcdn.net", "evil.example")), Code, Post))
    }
}
