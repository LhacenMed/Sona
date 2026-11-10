package com.lhacenmed.sona.core.downloader.sites.tiktok

import java.security.MessageDigest
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PostId = "900000001"
private const val Page = "https://www.tiktok.com/@synthetic/video/$PostId"

/** A «Please wait…» page shaped like TikTok's: padding stripped from `cs`, an empty `rs`. */
private fun wafPage(answer: Int = 4242, rs: String = "", digest: ByteArray? = null): String {
    val base = ByteArray(32) { it.toByte() }
    val expected = digest ?: MessageDigest.getInstance("SHA-256").digest(base + answer.toString().toByteArray())
    val b64 = Base64.getEncoder()
    val cs = b64.encodeToString("""{"v":{"a":"${b64.encodeToString(base)}","b":1793061631,"c":"${b64.encodeToString(expected)}"},"s":"c3ludGhldGlj"}"""
        .toByteArray()).trimEnd('=')
    return """<!DOCTYPE html><html><head></head><body>Please wait...
        <p id="wci" class="_wafchallengeid"></p><p id="cs" class="$cs"></p>
        <p id="rci" class="waforiginalreid"></p><p id="rs" class="$rs"></p></body></html>"""
}

private fun page(json: String) = "<html><script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">$json</script></html>"

private val videoItem = """{"__DEFAULT_SCOPE__":{"webapp.video-detail":{"statusCode":0,"itemInfo":{"itemStruct":{"id":"$PostId",
    "desc":"A synthetic clip\nsecond line","author":{"uniqueId":"synthetic","nickname":"Synthetic"},
    "video":{"duration":12,"width":720,"height":1280,"cover":"https://p16-sign.example.tiktokcdn.com/cover.jpeg",
    "playAddr":"https://v16-webapp-prime.tiktok.com/video/tos/play.mp4?a=1",
    "bitrateInfo":[
      {"GearName":"normal_720_0","Bitrate":1200000,"CodecType":"h264","PlayAddr":{"Width":720,"Height":1280,"DataSize":1800000,
        "UrlList":["https://evil.example/a.mp4","https://v16-webapp-prime.tiktok.com/video/tos/720.mp4?a=1"]}},
      {"GearName":"adapt_540_1","Bitrate":600000,"CodecType":"h265_hvc1","PlayAddr":{"Width":540,"Height":960,
        "UrlList":["https://v19-webapp-prime.tiktok.com/video/tos/540.mp4?a=1"]}}]}}}}}}"""

class TikTokReaderTest {
    private fun solve(page: String) = TikTokWafChallenge.solve(page)

    @Test
    fun postLinksKeepTheUserAndId() {
        val photo = checkNotNull(TikTokPostIdentity.parse("https://www.tiktok.com/@synthetic/photo/$PostId?tracking=fake"))
        assertEquals(Page, photo.videoUrl)
        assertNull(TikTokPostIdentity.parse("https://www.tiktok.com.evil.example/@synthetic/video/$PostId"))
        assertNull(TikTokPostIdentity.parse("https://www.tiktok.com/@synthetic"))
        assertTrue(TikTokReader.accepts("https://vt.tiktok.com/ZSabc123/"))
        assertFalse(TikTokReader.accepts("https://vm.tiktok.com/"))
    }

    @Test
    fun theChecksAnswerIsTheCompactChallengeAndTheNumberFound() {
        val cookie = checkNotNull(solve(wafPage(4242)))
        assertTrue(cookie.startsWith("_wafchallengeid="))
        assertFalse(cookie.contains(";"))
        val json = String(Base64.getDecoder().decode(cookie.substringAfter('=')))
        val base = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        assertTrue(json, json.startsWith("""{"v":{"a":"$base","b":1793061631,"c":"""))
        assertTrue(json, json.endsWith(""""s":"c3ludGhldGlj","d":"${Base64.getEncoder().encodeToString("4242".toByteArray())}"}"""))
        assertTrue(checkNotNull(solve(wafPage(rs = "abc123"))).endsWith("; waforiginalreid=abc123"))
        assertFalse(checkNotNull(solve(wafPage(rs = "a b;c"))).contains("waforiginalreid"))
    }

    @Test
    fun ordinaryPagesAndUnsolvableChecksAreNotAnswered() {
        assertNull(solve(page(videoItem)))
        assertNull(solve(wafPage(digest = ByteArray(32))))
        assertNull(solve(wafPage(digest = ByteArray(5))))
        assertNull(solve("""<p id="wci" class="_wafchallengeid"></p><p id="cs" class="not base64!"></p>"""))
        assertNull(solve(wafPage() + " ".repeat(40 * 1024)))
    }

    @Test
    fun aVideoPostsQualitiesBecomeFormatsCarryingTheCookie() {
        val info = JSONObject(checkNotNull(TikTokReader.parse(page(videoItem), PostId, Page, "token1")))
        val formats = info.getJSONArray("formats")
        assertEquals(2, formats.length())
        val hd = formats.getJSONObject(0)
        assertEquals("normal_720_0", hd.getString("format_id"))
        assertEquals("https://v16-webapp-prime.tiktok.com/video/tos/720.mp4?a=1", hd.getString("url"))
        assertEquals(1280, hd.getInt("height"))
        assertEquals(1800000L, hd.getLong("filesize"))
        assertEquals("tt_chain_token=token1", hd.getJSONObject("http_headers").getString("Cookie"))
        assertEquals("h265", formats.getJSONObject(1).getString("vcodec"))
        assertEquals("A synthetic clip", info.getString("title"))
        assertEquals(12.0, info.getDouble("duration"), 0.0)
        assertEquals("TikTok", info.getString("extractor_key"))
    }

    @Test
    fun aPhotoPostAnotherPostOrAnUnavailableOneIsNotRead() {
        val photos = checkNotNull(javaClass.getResource("/sites/tiktok/photo-post.json")).readText()
        assertNull(TikTokReader.parse(page(photos), PostId, Page, "t"))
        assertNull(TikTokReader.parse(page(videoItem), "900000002", Page, "t"))
        assertNull(TikTokReader.parse(page(videoItem.replace("\"statusCode\":0", "\"statusCode\":10216")), PostId, Page, "t"))
        assertNull(TikTokReader.parse(page(videoItem.replace("v16-webapp-prime.tiktok.com", "evil.example").replace("v19-webapp-prime.tiktok.com", "evil.example")), PostId, Page, "t"))
        assertNotNull(TikTokReader.parse(page(videoItem.replace("\"bitrateInfo\"", "\"unused\"")), PostId, Page, "t"))
    }
}
