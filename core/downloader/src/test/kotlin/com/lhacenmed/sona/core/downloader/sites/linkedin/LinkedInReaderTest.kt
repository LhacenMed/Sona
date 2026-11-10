package com.lhacenmed.sona.core.downloader.sites.linkedin

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val Activity = "7100000000000000003"
private const val Post = "https://www.linkedin.com/posts/someone_title-activity-$Activity-AbCd/"
private const val Stream720 = "https://dms.licdn.com/playlist/vid/v2/SYNTHETIC/mp4-720p-30fp-crf28/0/1?e=2147483647&v=beta&t=sig"
private const val Stream360 = "https://dms.licdn.com/playlist/vid/v2/SYNTHETIC/mp4-360p-30fp-crf28/0/1?e=2147483647&v=beta&t=sig"

private fun videoObject(id: String = Post) =
    """<script type="application/ld+json">{"@context":"http://schema.org","@type":"VideoObject","@id":"$id","name":"Synthetic video title",
    "contentUrl":"$Stream720","thumbnailUrl":"https://media.licdn.com/dms/image/v2/cover.jpg","duration":"PT51S",
    "creator":{"@type":"Person","name":"Synthetic Author"}}</script>"""

/** The older layout: one post container owning a `video[data-sources]` in its `feed-native-video-content`. */
private fun containerPage(urn: String = "urn:li:activity:$Activity", jsonLd: String = videoObject(), extra: String = "") =
    """<!DOCTYPE html><html><head><link rel="canonical" href="$Post"/>$jsonLd</head><body>
    <article data-activity-urn="$urn"><div data-test-id="feed-native-video-content">
    <video class="share-native-video__node" data-sources="[{&quot;src&quot;:&quot;${Stream720.replace("&", "&amp;")}&quot;,&quot;type&quot;:&quot;video/mp4&quot;,&quot;data-bitrate&quot;:1500000},{&quot;src&quot;:&quot;${Stream360.replace("&", "&amp;")}&quot;,&quot;type&quot;:&quot;video/mp4&quot;}]"></video>
    </div>$extra</article></body></html>"""

/** The newer layout: a video-js player, the page itself naming the video post. */
private fun playerPage(canonical: String = Post, jsonLd: String = videoObject()) = """<!DOCTYPE html><html><head>
    <meta property="og:type" content="video.other"/><link rel="canonical" href="$canonical"/><meta property="og:url" content="$canonical"/>
    $jsonLd</head><body><div data-vjs-player class="video-js"></div></body></html>"""

class LinkedInReaderTest {
    private val resolved = checkNotNull(LinkedInPostIdentity.parse(Post))

    @Test
    fun entryHostsAndUpdateShapesNameOnePost() {
        for (host in listOf("linkedin.com", "ae.linkedin.com", "www.linkedin.com"))
            assertEquals(Post, LinkedInPostIdentity.parse("https://$host/posts/someone_title-activity-$Activity-AbCd/?tracking=x")?.cleanUrl)
        val cases = mapOf(
            "https://www.linkedin.com/feed/update/urn:li:share:7100000000000000001/" to "share:7100000000000000001",
            "https://www.linkedin.com/embed/feed/update/urn:li:ugcPost:7100000000000000002" to "ugcPost:7100000000000000002",
            "https://linkedin.com/feed/update/urn%3Ali%3Aactivity%3A7100000000000000003/?utm=x" to "activity:7100000000000000003")
        for ((url, urn) in cases) assertEquals(url, "https://www.linkedin.com/feed/update/urn:li:$urn/", LinkedInPostIdentity.parse(url)?.cleanUrl)
        assertTrue(checkNotNull(LinkedInPostIdentity.parse("https://lnkd.in/p/AbC123")).isShortlink)
        for (url in listOf("https://www.linkedin.com/feed/update/urn:li:comment:1/", "https://evil.linkedin.com/posts/a-activity-1/",
            "https://ae.linkedin.com.evil.com/posts/a-activity-1/", "https://lnkd.in/AbC", "https://www.linkedin.com/embed/posts/someone-activity-1/"))
            assertNull(url, LinkedInPostIdentity.parse(url))
        assertTrue(LinkedInPostIdentity.isLoginWall(checkNotNull(HttpsUrl.parse("https://www.linkedin.com/signup/cold-join"))))
    }

    @Test
    fun aRedirectKeepsToOnePost() {
        fun url(value: String) = checkNotNull(HttpsUrl.parse(value))
        assertTrue(LinkedInPostIdentity.allowsHop(url("https://lnkd.in/p/AbC123"), url(Post)))
        assertTrue(LinkedInPostIdentity.allowsHop(url("https://linkedin.com/posts/x-activity-$Activity-y/"), url("https://www.linkedin.com/feed/update/urn:li:activity:$Activity/")))
        assertFalse(LinkedInPostIdentity.allowsHop(url(Post), url("https://www.linkedin.com/feed/update/urn:li:activity:1/")))
        assertFalse(LinkedInPostIdentity.allowsHop(url(Post), url("https://evil.test/posts/x-activity-$Activity/")))
    }

    @Test
    fun theOlderLayoutsDataSourcesBecomeFormats() {
        val info = JSONObject(checkNotNull(LinkedInReader.parse(containerPage(), resolved)))
        val formats = info.getJSONArray("formats")
        assertEquals(2, formats.length())
        assertEquals("mp4-720p", formats.getJSONObject(0).getString("format_id"))
        assertEquals(720, formats.getJSONObject(0).getInt("height"))
        assertEquals(1500.0, formats.getJSONObject(0).getDouble("tbr"), 0.0)
        assertEquals(Stream720, formats.getJSONObject(0).getString("url"))
        assertEquals("Synthetic video title", info.getString("title"))
        assertEquals("Synthetic Author", info.getString("uploader"))
        assertEquals(51.0, info.getDouble("duration"), 0.0)
        assertEquals(Post, info.getString("webpage_url"))
    }

    @Test
    fun theNewerLayoutsVideoObjectIsItsFile() {
        val info = JSONObject(checkNotNull(LinkedInReader.parse(playerPage(), resolved)))
        assertEquals(Stream720, info.getJSONArray("formats").getJSONObject(0).getString("url"))
    }

    @Test
    fun aPageThatDoesNotProveItIsThisVideoPostIsNotRead() {
        assertNull(LinkedInReader.parse(containerPage(urn = "urn:li:activity:1"), resolved))
        assertNull(LinkedInReader.parse(containerPage(jsonLd = ""), resolved))
        assertNull(LinkedInReader.parse(containerPage(jsonLd = videoObject("https://www.linkedin.com/posts/other-activity-1/")), resolved))
        assertNull(LinkedInReader.parse(containerPage(extra = "<ul class=\"feed-images-content\"></ul>"), resolved))
        assertNull(LinkedInReader.parse(containerPage().replace("<article", "<template><article").replace("</article>", "</article></template>"), resolved))
        assertNull(LinkedInReader.parse(playerPage(canonical = "https://www.linkedin.com/posts/other-activity-1/"), resolved))
        assertNull(LinkedInReader.parse(playerPage(jsonLd = videoObject() + "<script type=\"application/ld+json\">{\"@type\":\"SocialMediaPosting\",\"@id\":\"$Post\"}</script>"), resolved))
        assertNull(LinkedInReader.parse(playerPage(jsonLd = videoObject().replace("dms.licdn.com", "evil.example")), resolved))
    }
}
