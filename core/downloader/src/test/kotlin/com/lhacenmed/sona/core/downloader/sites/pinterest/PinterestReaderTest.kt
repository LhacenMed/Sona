package com.lhacenmed.sona.core.downloader.sites.pinterest

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val Pin = "100000000000000001"
private const val Canonical = "https://www.pinterest.com/pin/$Pin/"

private const val Videos = """{"video_list":{"V_720P":{"url":"https://v1.pinimg.com/videos/mc/720p/aa/bb/cc/synthetic.mp4","width":720,"height":1280,
    "duration":15000,"thumbnail":"https://i.pinimg.com/videos/thumbnails/originals/aa/bb/cc/synthetic.0000000.jpg"},
    "V_HLSV4":{"url":"https://v1.pinimg.com/videos/mc/hls/aa/bb/cc/synthetic.m3u8","width":720,"height":1280}}}"""

private fun reduxPage(pin: String) = """<!DOCTYPE html><script id="__PWS_INITIAL_PROPS__" type="application/json">
    {"initialReduxState":{"pins":{"$Pin":$pin}}}</script>"""

private val videoPin = """{"id":"$Pin","is_video":true,"grid_title":"Synthetic video pin","pinner":{"full_name":"Synthetic Pinner"},"videos":$Videos}"""

class PinterestReaderTest {
    @Test
    fun countrySlugAndInviteLinksAreTheSamePin() {
        for (url in listOf(Canonical, "https://pinterest.com/pin/$Pin", "https://www.pinterest.co.uk/pin/synthetic-title--$Pin/sent/?trk=1",
            "https://co.pinterest.com/pin/$Pin/", "https://in.pinterest.com/pin/Synthetic_Title--$Pin"))
            assertEquals(url, Canonical, PinterestPostIdentity.parse(url)?.fetchUrl)
        for (url in listOf("https://pin.it/AbCd123", "https://www.pinterest.com/synthetic/board/", "https://www.pinterest.com/pin/not-a-number/",
            "https://www.pinterest.com.evil.example/pin/$Pin/", "http://www.pinterest.com/pin/$Pin/", "https://www.pinterest.com/pin/$Pin/repins/"))
            assertNull(url, PinterestPostIdentity.parse(url))
        assertTrue(PinterestPostIdentity.isLoginWall(checkNotNull(HttpsUrl.parse("https://www.pinterest.com/login/"))))
        assertTrue(PinterestReader.accepts("https://api.pinterest.com/url_shortener/AbCd123/redirect/"))
    }

    @Test
    fun aVideoPinsMp4sBecomeFormats() {
        val info = JSONObject(checkNotNull(PinterestReader.parsePage(reduxPage(videoPin), Pin, Canonical)))
        val formats = info.getJSONArray("formats")
        assertEquals(1, formats.length())
        assertEquals("V_720P", formats.getJSONObject(0).getString("format_id"))
        assertEquals(1280, formats.getJSONObject(0).getInt("height"))
        assertEquals("Synthetic video pin", info.getString("title"))
        assertEquals("Synthetic Pinner", info.getString("uploader"))
        assertEquals(15.0, info.getDouble("duration"), 0.0)
    }

    @Test
    fun anIdeaPinsOneVideoBlockAndTheWidgetAreRead() {
        val story = """{"id":"$Pin","story_pin_data":{"pages":[{"blocks":[{"type":"text"},{"video":$Videos}]}]}}"""
        assertEquals(1, JSONObject(checkNotNull(PinterestReader.parsePage(reduxPage(story), Pin, Canonical))).getJSONArray("formats").length())
        val widget = """{"status":"success","data":[$videoPin]}"""
        assertEquals(Pin, JSONObject(checkNotNull(PinterestReader.parseWidget(widget, Pin, Canonical))).getString("id"))
        assertNull(PinterestReader.parseWidget("""{"status":"success","data":[${videoPin.replace("\"id\":\"$Pin\"", "\"id\":\"2\"")}]}""", Pin, Canonical))
    }

    @Test
    fun anImagePinOrCopiesThatDisagreeAreNotRead() {
        val image = """{"id":"$Pin","images":{"orig":{"url":"https://i.pinimg.com/originals/01/23/45/0123456789abcdef0123456789abcdef.jpg"}}}"""
        assertNull(PinterestReader.parsePage(reduxPage(image), Pin, Canonical))
        val both = reduxPage(videoPin) + """<script id="resource-response-data" type="application/json">
            {"resource":{"name":"PinResource"},"resource_response":{"data":$image}}</script>"""
        assertNull(PinterestReader.parsePage(both, Pin, Canonical))
        assertNull(PinterestReader.parsePage(reduxPage(videoPin.replace("v1.pinimg.com", "evil.example")), Pin, Canonical))
    }
}
