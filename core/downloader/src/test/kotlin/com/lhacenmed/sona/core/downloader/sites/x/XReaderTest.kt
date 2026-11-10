package com.lhacenmed.sona.core.downloader.sites.x

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val Id = "1790000000000000001"
private const val Page = "https://x.com/someone/status/$Id"

class XReaderTest {
    private val fixture = checkNotNull(javaClass.getResource("/sites/x/syndication-video.json")).readText()
    private fun root() = JSONObject(fixture)
    private fun build(root: JSONObject = root(), index: Int? = null) = XVideoInfo.build(root, Id, index, Page)
    private fun info(root: JSONObject = root()) = JSONObject(checkNotNull(build(root)))
    private fun ids(info: JSONObject) = info.getJSONArray("formats").let { formats -> (0 until formats.length()).map { formats.getJSONObject(it).getString("format_id") } }
    private fun variants(root: JSONObject) = root.getJSONArray("mediaDetails").getJSONObject(0).getJSONObject("video_info").getJSONArray("variants")
    private fun withVariant(variant: String) = root().also { variants(it).put(JSONObject(variant)) }

    @Test
    fun mirrorsUseOnlyThePostId() {
        for (host in listOf("fxtwitter.com", "vxtwitter.com", "fixupx.com", "fixvx.com")) for (prefix in listOf("", "www.")) {
            val identity = checkNotNull(XPostIdentity.parse("https://$prefix$host/synthetic/status/$Id"))
            assertTrue(identity.syndicationUrl.startsWith("https://cdn.syndication.twimg.com/"))
            assertEquals("https://x.com/i/status/$Id", identity.canonicalUrl)
            assertNull(XPostIdentity.parse("https://$host.evil.com/synthetic/status/$Id"))
        }
    }

    @Test
    fun acceptedPostShapesKeepOnlyTheNumericId() {
        for (url in listOf("https://x.com/someone/status/$Id", "https://twitter.com/someone/status/$Id/",
            "https://mobile.twitter.com/some_one/status/$Id?s=20&t=Synthetic", "https://www.x.com/someone/status/$Id",
            "https://x.com/i/status/$Id", "https://x.com/i/web/status/$Id", "https://x.com/someone/status/$Id/photo/1",
            "https://x.com/someone/status/$Id/photo/4", "https://twitter.com/someone/status/$Id/video/1", "https://X.com/someone/status/$Id",
            "https://m.x.com/someone/status/$Id?s=20", "https://d.fxtwitter.com/someone/status/$Id", "https://fxtwitter.com/someone/status/$Id/ar"))
            assertEquals(url, Id, XPostIdentity.parse(url)?.postId)
        assertEquals(2, XPostIdentity.parse("https://x.com/someone/status/$Id/video/2")?.mediaIndex)
        assertNull(XPostIdentity.parse("https://fxtwitter.com/someone/status/$Id/video/2")?.mediaIndex)
    }

    @Test
    fun otherPathsHostsAndSchemesAreRejected() {
        for (url in listOf("https://x.com/someone", "https://x.com/home", "https://x.com/someone/status/", "https://x.com/someone/status/abc",
            "https://x.com/someone/status/0123", "https://x.com/someone/status/12/likes", "https://x.com/someone/status/12/photo/5",
            "https://x.com/i/spaces/1AbC", "https://x.com/a.b/status/12", "https://x.com/abcdefghijklmnop/status/12",
            "https://x.com.evil.example/a/status/12", "https://evilx.com/a/status/12", "http://x.com/a/status/12",
            "https://x.com:8443/a/status/12", "https://user@x.com/a/status/12", "https://x.com/a/status/99999999999999999999",
            "https://x.com/someone/status/$Id/ar", "https://e.fxtwitter.com/someone/status/$Id"))
            assertNull(url, XPostIdentity.parse(url))
    }

    @Test
    fun tokenMatchesJavaScripts() {
        assertEquals("https://cdn.syndication.twimg.com/tweet-result?id=1234567890123456789&token=2zqic77uqyk",
            XPostIdentity.parse("https://x.com/someone/status/1234567890123456789?s=46")?.syndicationUrl)
        val vectors = listOf("1221368500568277588" to "2yl1itydzdl", "1395494634720187923" to "3ds2q3ifpkb",
            "1136879138292273062" to "2r7m1rfnds", "1606832857977215304" to "3w8ija5eh5", "1839042189418924927" to "4ghirrxe9a5",
            "1" to "bhi2ay3f28n", "20" to "6dq1a2xwd93", "123456789" to "ng9m4sqe3h", Id to "4c7g8auqyik")
        for ((id, token) in vectors) assertEquals(id, token, XPostIdentity.token(id))
        val radix = listOf(0.5 to "0.i", 1.0 / 3 to "0.c", 123.456 to "3f.gez4w97ry", 1e20 to "l3r41ifs0p800",
            1152921504606859300.0 to "8rc4kbdvt1k0", 0.1 to "0.3lllllllllm", 0.0 to "0", 35.0 to "z", 36.0 to "10")
        for ((value, expected) in radix) assertEquals(value.toString(), expected, XPostIdentity.jsRadixString(value, 36))
    }

    @Test
    fun progressiveVariantsBecomeYtDlpsHttpRows() {
        val info = info()
        assertEquals(listOf("http-632", "http-950", "http-2176"), ids(info))
        val top = info.getJSONArray("formats").getJSONObject(2)
        assertEquals("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/720x1280/Synthetic.mp4?tag=14", top.getString("url"))
        assertEquals("https", top.getString("protocol"))
        assertEquals(1280, top.getInt("height"))
        assertEquals(720, top.getInt("width"))
        assertEquals("aac", top.getString("acodec"))
        assertEquals("Synthetic Author - Synthetic post text line", info.getString("title"))
        assertEquals("Synthetic Author", info.getString("uploader"))
        assertEquals(13.04, info.getDouble("duration"), 0.001)
        assertEquals("Twitter", info.getString("extractor_key"))
        assertEquals(Page, info.getString("webpage_url"))
        assertEquals("https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/SyntheticPoster.jpg?name=orig", info.getString("thumbnail"))
    }

    @Test
    fun aGifIsOneSilentRowSizedByItsOriginal() {
        val gif = root().apply {
            getJSONArray("mediaDetails").getJSONObject(0).put("type", "animated_gif").put("video_info", JSONObject(
                """{"variants":[{"bitrate":0,"content_type":"video/mp4","url":"https://video.twimg.com/tweet_video/Synthetic.mp4"}]}"""))
        }
        val row = info(gif).getJSONArray("formats").getJSONObject(0)
        assertEquals("http", row.getString("format_id"))
        assertEquals("none", row.getString("acodec"))
        assertEquals(1280, row.getInt("height"))
        assertNull("a GIF picked by index is not a video", build(gif, 1))
        assertNull(build(root().apply { getJSONArray("mediaDetails").getJSONObject(0).put("type", "animated_gif") }))
    }

    @Test
    fun idsUseYtDlpsIntegerDivisionAndBitrateFallback() {
        fun extra(bitrate: String) = ids(info(withVariant("""{$bitrate"content_type":"video/mp4","url":"https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/1280x720/Extra.mp4"}"""))).last()
        assertEquals("http-1", extra(""""bitrate":1500,"""))
        assertEquals("http-3000", extra(""""bitrate":0,"bit_rate":3000000,"""))
        for (bad in listOf(""""bitrate":"2176000",""", """"bitrate":-1000,""", """"bitrate":1.5e6,"""))
            assertNull(bad, build(withVariant("""{$bad"content_type":"video/mp4","url":"https://video.twimg.com/ext_tw_video/a/1x1/a.mp4"}""")))
    }

    @Test
    fun anyUnknownShapeIsLeftToYtDlp() {
        val cases = listOf(
            withVariant("""{"bitrate":632000,"content_type":"video/mp4","url":"https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/320x568/Duplicate.mp4"}"""),
            withVariant("""{"bitrate":1,"content_type":"video/webm","url":"https://video.twimg.com/ext_tw_video/a.mp4"}"""),
            withVariant("""{"bitrate":1000,"content_type":"video/mp4","url":"https://example.invalid/vid/a.mp4"}"""),
            withVariant("""{"bitrate":1000,"content_type":"video/mp4","url":"http://video.twimg.com/ext_tw_video/a.mp4"}"""),
            withVariant("""{"bitrate":1000,"content_type":"video/mp4"}"""),
            root().apply { put("card", "{}") },
            root().apply { put("quoted_tweet", JSONObject("""{"id_str":"1"}""")) },
            root().apply { put("retweeted_status", JSONObject("""{"id_str":"1"}""")) },
            root().apply { put("possibly_sensitive", true) },
            root().apply { getJSONArray("mediaDetails").getJSONObject(0).getJSONObject("video_info").put("variants", JSONArray()) },
            root().apply { put("__typename", "TweetTombstone") },
            root().apply { put("id_str", "1790000000000000002") },
            root().apply { getJSONArray("mediaDetails").getJSONObject(0).put("type", "audio_space") },
            root().apply { remove("mediaDetails") },
            root().apply { put("mediaDetails", JSONArray("""[{"type":"photo"}]""")) },
        )
        for ((index, case) in cases.withIndex()) assertNull("case $index", build(case))
    }

    @Test
    fun aMediaIndexPicksWhatYtDlpReads() {
        val photoThenVideo = root().apply {
            val media = getJSONArray("mediaDetails")
            put("mediaDetails", JSONArray().put(JSONObject("""{"type":"photo"}""")).put(media.getJSONObject(0)))
        }
        assertEquals(JSONObject(checkNotNull(build())).getJSONArray("formats").length(), info(photoThenVideo).getJSONArray("formats").length())
        assertNotNull(build(photoThenVideo, 2))
        assertNull(build(photoThenVideo, 1))
        assertNull(build(root(), 2))
        val twoVideos = root().apply { getJSONArray("mediaDetails").put(JSONObject(getJSONArray("mediaDetails").getJSONObject(0).toString())) }
        assertTrue(info(twoVideos).getString("title").endsWith(" #1"))
        assertNull(build(twoVideos, 2))
    }

    @Test
    fun titleStripsLinksAndCutsAt72CodePoints() {
        assertEquals("Synthetic Author - Synthetic post", info(root().put("text", "Synthetic post https://t.co/Synthetic")).getString("title"))
        val long = "😀".repeat(80)
        assertEquals("Synthetic Author - " + "😀".repeat(69) + "...", info(root().put("text", "$long https://t.co/Synthetic")).getString("title"))
        assertFalse(info(root().apply { remove("user") }).has("uploader"))
    }
}
