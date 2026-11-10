package com.lhacenmed.sona.core.downloader.sites.facebook

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val VideoId = "900000000000001"
private const val Hd = "https://video.fexample1-1.fna.fbcdn.net/o1/v/t2/f2/m69/hd.mp4?efg=1"
private const val Sd = "https://video.fexample1-1.fna.fbcdn.net/o1/v/t2/f2/m69/sd.mp4?efg=1"

/** A logged-out page's route and Relay blocks, as served, with [data] as one Relay result. */
private fun page(data: String, route: String = "FBReelsRoot.react") = """<!DOCTYPE html><html><head>
    <meta property="og:title" content="Synthetic page title"/></head><body>
    <script type="application/json" data-sjs>{"require":[["ScheduledServerJS","handle",null,[{"__bbox":{"require":[["CometPlatformRootClient","init",[],
    [{"initialRouteInfo":{"route":{"rootView":{"resource":{"__dr":"$route"}}}}}]]]}}]]]}</script>
    <script type="application/json" data-sjs>{"require":[["ScheduledServerJS","handle",null,[{"__bbox":{"require":[["RelayPrefetchedStreamCache","next",[],
    ["adp_1",{"__bbox":{"complete":true,"result":{"data":$data}}}]]]}}]]]}</script></body></html>"""

private fun videoNode(hd: String? = Hd) = """{"__typename":"Video","id":"$VideoId","width":720,"height":1280,"playable_duration_in_ms":15500,
    "owner":{"name":"Synthetic Owner"},"preferred_thumbnail":{"image":{"uri":"https://scontent.example.fbcdn.net/thumb.jpg"}},
    "videoDeliveryLegacyFields":{"id":"$VideoId","browser_native_hd_url":${hd?.let { "\"$it\"" } ?: "null"},"browser_native_sd_url":"$Sd"}}"""

private fun story(attachments: String, postId: String = "900000000000003") = """{"node_v2":{"__typename":"Story","post_id":"$postId",
    "message":{"text":"Synthetic message\nsecond"},"actors":[{"name":"Synthetic Actor"}],"attachments":$attachments}}"""

class FacebookReaderTest {
    private fun identity(url: String) = checkNotNull(FacebookPostIdentity.parse(url))

    @Test
    fun acceptedShapesAreMadeWwwsWithTheirIdentifyingQuery() {
        val cases = mapOf(
            "https://m.facebook.com/share/v/Xyz12?mibextid=abc" to "https://www.facebook.com/share/v/Xyz12",
            "https://facebook.com/story.php?story_fbid=123456789&id=42&rdid=x" to "https://www.facebook.com/story.php?story_fbid=123456789&id=42",
            "https://www.facebook.com/some.page/posts/pfbid02xYz?__cft__=t" to "https://www.facebook.com/some.page/posts/pfbid02xYz",
            "https://www.facebook.com/reel/1195289147628387" to "https://www.facebook.com/reel/1195289147628387",
            "https://m.facebook.com/watch/?v=777" to "https://www.facebook.com/watch/?v=777",
            "https://www.facebook.com/cnn/videos/10155529876156509/" to "https://www.facebook.com/cnn/videos/10155529876156509/",
            "https://fb.watch/aBc_12-/" to "https://fb.watch/aBc_12-/",
            "https://www.facebook.com/groups/123456/?multi_permalinks=987654321" to "https://www.facebook.com/groups/123456/posts/987654321/",
            "https://ar-ar.facebook.com/watch/?v=777" to "https://www.facebook.com/watch/?v=777",
        )
        for ((input, fetch) in cases) assertEquals(input, fetch, FacebookPostIdentity.parse(input)?.fetchUrl)
        assertTrue(identity("https://www.facebook.com/share/r/Synthetic/").isVideoShare)
        assertFalse(identity("https://www.facebook.com/share/p/Synthetic/").isVideoShare)
        assertEquals("777", identity("https://www.facebook.com/watch/?v=777").videoId)
        assertNull(identity("https://www.facebook.com/permalink.php?story_fbid=pfbid0AbCd&id=42").postId)
        for (url in listOf("http://www.facebook.com/share/p/1AbC/", "https://www.facebook.com.evil.example/share/p/1AbC/",
            "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com", "https://www.facebook.com/", "https://www.facebook.com/stories/1/",
            "https://www.facebook.com/events/1/", "https://www.facebook.com/watch/?v=1#frag", "https://www.facebook.com/photo/?fbid=1&fbid=2"))
            assertNull(url, FacebookPostIdentity.parse(url))
        assertTrue(FacebookPostIdentity.isLoginWall(checkNotNull(HttpsUrl.parse("https://m.facebook.com/login.php?next=x"))))
        assertFalse(FacebookPostIdentity.isLoginWall(checkNotNull(HttpsUrl.parse("https://www.facebook.com/loginx/"))))
    }

    @Test
    fun aReelsVideoNodeNamesItsFile() {
        val reel = identity("https://www.facebook.com/reel/$VideoId")
        val info = JSONObject(checkNotNull(FacebookReader.parse(page("""{"video":${videoNode()}}"""), reel, reel, reel.fetchUrl)))
        val format = info.getJSONArray("formats").getJSONObject(0)
        assertEquals("hd", format.getString("format_id"))
        assertEquals(Hd, format.getString("url"))
        assertEquals(1280, format.getInt("height"))
        assertEquals(15.5, info.getDouble("duration"), 0.0)
        assertEquals("Synthetic Owner", info.getString("uploader"))
        assertEquals("Synthetic page title", info.getString("title"))
        val sdOnly = JSONObject(checkNotNull(FacebookReader.parse(page("""{"video":${videoNode(hd = null)}}"""), reel, reel, reel.fetchUrl)))
        assertEquals(Sd, sdOnly.getJSONArray("formats").getJSONObject(0).getString("url"))
    }

    @Test
    fun aPostWhoseOnlyAttachmentIsOneVideoIsThatVideo() {
        val post = identity("https://www.facebook.com/somepage/posts/900000000000003/")
        val data = story("""[{"media":{"__typename":"Video","id":"$VideoId"}}]""").dropLast(1) + ""","extra":${videoNode()}}"""
        val info = JSONObject(checkNotNull(FacebookReader.parse(page(data, "CometSinglePostRoot.react"), post, post, post.fetchUrl)))
        assertEquals("Synthetic message", info.getString("title"))
        assertEquals("Synthetic Actor", info.getString("uploader"))
    }

    @Test
    fun photosOtherPostsErrorPagesAndForeignFilesAreNotRead() {
        val post = identity("https://www.facebook.com/somepage/posts/900000000000003/")
        val mixed = story("""[{"media":{"__typename":"Video","id":"$VideoId"}},{"styles":{"attachment":{"media":{"__typename":"Photo","id":"5"}}}}]""")
            .dropLast(1) + ""","extra":${videoNode()}}"""
        assertNull(FacebookReader.parse(page(mixed), post, post, post.fetchUrl))
        val other = story("""[{"media":{"__typename":"Video","id":"$VideoId"}}]""", postId = "900000000000009").dropLast(1) + ""","extra":${videoNode()}}"""
        assertNull(FacebookReader.parse(page(other), post, post, post.fetchUrl))
        val reel = identity("https://www.facebook.com/reel/$VideoId")
        assertNull(FacebookReader.parse(page("""{"video":${videoNode()}}""", "CometErrorRoot.react"), reel, reel, reel.fetchUrl))
        assertNull(FacebookReader.parse(page("""{"video":${videoNode().replace("video.fexample1-1.fna.fbcdn.net", "evil.example")}}"""), reel, reel, reel.fetchUrl))
        assertFalse(FacebookReader.accepts("https://www.facebook.com/photo/?fbid=555"))
    }
}
