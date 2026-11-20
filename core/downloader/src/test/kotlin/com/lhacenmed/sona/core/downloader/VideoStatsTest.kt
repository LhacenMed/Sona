package com.lhacenmed.sona.core.downloader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStatsTest {

    @Test
    fun `the page a video came from is kept with its counts, and read back`() {
        val json = videoStatsJsonOf(JSONObject().put("view_count", 12).put("webpage_url", "https://youtu.be/abc").put("title", "x"))!!

        val stats = EmbeddedExtras.statsOf(JSONObject(json))!!
        assertEquals(12L, stats.viewCount)
        assertEquals("https://youtu.be/abc", stats.sourceUrl)
        assertFalse(JSONObject(json).has("title"))
    }

    @Test
    fun `a count shown rounded is the number it stands for`() {
        assertEquals(254_000L, com.lhacenmed.sona.core.downloader.sites.compactCountOf("254K"))
        assertEquals(2_800L, com.lhacenmed.sona.core.downloader.sites.compactCountOf("2.8K"))
        assertEquals(1_200_000L, com.lhacenmed.sona.core.downloader.sites.compactCountOf("1,2 M"))
        assertEquals(25_481L, com.lhacenmed.sona.core.downloader.sites.compactCountOf("25,481"))
        assertEquals(43L, com.lhacenmed.sona.core.downloader.sites.compactCountOf("43"))
        assertNull(com.lhacenmed.sona.core.downloader.sites.compactCountOf("lots"))
    }

    @Test
    fun `read again, a count the site no longer gives stays as it was, and every count round-trips`() {
        val old = com.lhacenmed.sona.core.model.VideoStats(1_000_000L, 10L, 5L, "https://a.example/v", commentCount = 3L)
        val merged = old.updatedBy(com.lhacenmed.sona.core.model.VideoStats(null, 12L, null, repostCount = 4L))

        assertEquals(com.lhacenmed.sona.core.model.VideoStats(1_000_000L, 12L, 5L, "https://a.example/v", 3L, 4L), merged)
        assertEquals(merged, EmbeddedExtras.statsOf(JSONObject(EmbeddedExtras.statsJsonOf(merged))))
    }

    @Test
    fun `a page alone is kept, to read the counts from later, but holds no counts`() {
        val stats = EmbeddedExtras.statsOf(JSONObject().put("webpage_url", "https://www.tiktok.com/@a/video/1"))!!

        assertFalse(stats.hasCounts)
        assertEquals("https://www.tiktok.com/@a/video/1", stats.sourceUrl)
    }

    @Test
    fun `a day alone dates it at UTC's midnight, and a page that is not a link is none`() {
        val stats = EmbeddedExtras.statsOf(JSONObject().put("upload_date", "20240501").put("webpage_url", "see my channel"))!!

        assertTrue(stats.hasCounts)
        assertEquals(1_714_521_600_000L, stats.uploadedAtMs)
        assertNull(stats.sourceUrl)
    }

    @Test
    fun `a read's day is made yt-dlp's own, in its entries too, or dropped where it holds none`() {
        val read = JSONObject().put("upload_date", "20261023Z")
            .put("entries", org.json.JSONArray().put(JSONObject().put("upload_date", "2026-10-23")).put(JSONObject().put("upload_date", "soon")))

        assertTrue(withYtDlpDay(read))
        assertEquals("20261023", read.getString("upload_date"))
        assertEquals("20261023", read.getJSONArray("entries").getJSONObject(0).getString("upload_date"))
        assertFalse(read.getJSONArray("entries").getJSONObject(1).has("upload_date"))
        assertFalse(withYtDlpDay(JSONObject().put("upload_date", "20261023")))
    }

    @Test
    fun `a read kept from before with a broken day is handed on mended`() {
        val extraction = extractionOf(JSONObject().put("id", "x").put("upload_date", "20261023Z").toString(), isComplete = true)

        assertEquals("20261023", JSONObject(extraction.json).getString("upload_date"))
    }
}
