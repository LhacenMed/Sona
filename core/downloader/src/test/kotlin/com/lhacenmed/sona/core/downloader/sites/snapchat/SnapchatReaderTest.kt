package com.lhacenmed.sona.core.downloader.sites.snapchat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val Id = "W7_EDlXWTBiXAEEniNoMPwAAYenN4dWF4Zmp2AaFBsSbqAaFBsGLEAAAAAQ"
private const val Video = "https://cf-st.sc-cdn.net/d/pP6U8zzdWPNfVJAcCHIZv.27.IRZXSOY?mo=Gmca"
private const val Captions = "https://bolt-gcdn.sc-cdn.net/3/5WR4dBdBZVA62A6oW8HKf.vtt?bo=EgkyAX1IAlAuYAE%3D&uc=46"

/** A Spotlight page as Snapchat serves it: a BreadcrumbList, then the post's VideoObject. */
private fun page(id: String = Id, contentUrl: String = Video, caption: String = Captions) = """
<html><head>
<script type="application/ld+json">{"@context":"https://schema.org","@type":"BreadcrumbList","itemListElement":[]}</script>
<script type="application/ld+json">{"@context":"https://schema.org","@type":"VideoObject",
"url":"https://www.snapchat.com/@em/spotlight/$id","name":"Em announces a Q&A","thumbnailUrl":"https://cf-st.sc-cdn.net/d/thumb.256",
"creator":{"@type":"Person","name":"em","alternateName":"em2"},"width":540,"height":960,"contentUrl":"$contentUrl",
"duration":"PT1M10S","encodingFormat":"video/mp4","caption":{"@type":"MediaObject","contentUrl":"$caption","encodingFormat":"text/vtt"}}</script>
</head><body></body></html>"""

class SnapchatReaderTest {
    @Test
    fun spotlightLinksAreReadByTheirId() {
        for (raw in listOf("https://www.snapchat.com/spotlight/$Id", "https://snapchat.com/@em/spotlight/$Id/")) {
            assertEquals("https://www.snapchat.com/spotlight/$Id", SnapchatSpotlightIdentity.parse(raw)?.fetchUrl)
        }
        for (raw in listOf("https://www.snapchat.com/@em", "https://example.com/spotlight/$Id", "http://www.snapchat.com/spotlight/$Id")) {
            assertNull(SnapchatSpotlightIdentity.parse(raw))
        }
    }

    @Test
    fun thePostsVideoObjectGivesItsFileAndItsSpokenCaptions() {
        val info = JSONObject(checkNotNull(SnapchatReader.parse(page(), Id, "https://www.snapchat.com/@em/spotlight/$Id")))
        val format = info.getJSONArray("formats").getJSONObject(0)
        assertEquals(Video, format.getString("url"))
        assertEquals(960, format.getInt("height"))
        assertEquals(70.0, info.getDouble("duration"), 0.0)
        assertEquals("em", info.getString("uploader"))
        val caption = info.getJSONObject("automatic_captions").getJSONArray("und").getJSONObject(0)
        assertEquals(Captions, caption.getString("url"))
        assertEquals("vtt", caption.getString("ext"))
    }

    @Test
    fun anotherPostOrAFileElsewhereIsNotRead() {
        assertNull(SnapchatReader.parse(page(id = "Another_post_id_0123456789"), Id, "https://www.snapchat.com/spotlight/$Id"))
        assertNull(SnapchatReader.parse(page(contentUrl = "https://evil.test/a.mp4"), Id, "https://www.snapchat.com/spotlight/$Id"))
        val withoutCaptions = JSONObject(checkNotNull(SnapchatReader.parse(page(caption = "https://evil.test/a.vtt"), Id, "https://www.snapchat.com/spotlight/$Id")))
        assertEquals(0, withoutCaptions.getJSONObject("automatic_captions").length())
    }
}
