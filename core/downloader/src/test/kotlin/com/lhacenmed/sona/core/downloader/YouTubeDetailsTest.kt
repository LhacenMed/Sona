package com.lhacenmed.sona.core.downloader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeDetailsTest {

    /** A watch page as served: its data in `ytInitialData`, shaped as YouTube shapes it. */
    private val page = """<html><script>var ytInitialPlayerResponse = {"playabilityStatus":{"status":"LOGIN_REQUIRED"}};</script>
        <script>var ytInitialData = {"contents":{"twoColumnWatchNextResults":{"results":{"results":{"contents":[
        {"videoPrimaryInfoRenderer":{"viewCount":{"videoViewCountRenderer":{"viewCount":{"simpleText":"1,014,591 views"},
        "originalViewCount":"1014591"}},"dateText":{"simpleText":"Premiered Oct 23, 2026"},
        "videoActions":{"a":[{"buttonViewModel":{"accessibilityText":"like this video along with 25,507 other people"}}]}}}]}}}},
        "engagementPanels":[{"engagementPanelSectionListRenderer":{"panelIdentifier":"engagement-panel-comments-section",
        "header":{"engagementPanelTitleHeaderRenderer":{"title":{"runs":[{"text":"Comments"}]},"contextualInfo":{"runs":[{"text":"1.9K"}]}}}}}]};</script></html>"""

    @Test
    fun `a watch page's own data gives its date, views, likes and comments, its player refused or not`() {
        val data = checkNotNull(YouTubeDetails.initialDataOf(page))
        val stats = checkNotNull(EmbeddedExtras.statsOf(JSONObject(checkNotNull(YouTubeDetails.statsJsonOf(data, "https://www.youtube.com/watch?v=NWANo9BRz6Y")))))

        assertEquals(1_014_591L, stats.viewCount)
        assertEquals(25_507L, stats.likeCount)
        assertEquals(1_900L, stats.commentCount)
        assertEquals(1_792_713_600_000L, stats.uploadedAtMs)
        assertEquals("https://www.youtube.com/watch?v=NWANo9BRz6Y", stats.sourceUrl)
    }

    @Test
    fun `its day is read from however the page words it`() {
        assertEquals("20261023", YouTubeDetails.dayOf("Oct 23, 2026"))
        assertEquals("20260105", YouTubeDetails.dayOf("Streamed live on Jan 5, 2026"))
        assertNull(YouTubeDetails.dayOf("3 hours ago"))
    }

    @Test
    fun `only one YouTube video's link is a watch page`() {
        assertEquals("https://www.youtube.com/watch?v=NWANo9BRz6Y", YouTubeDetails.watchUrlOf("https://youtu.be/NWANo9BRz6Y?si=d3r4elsQWbxLemOl"))
        assertNull(YouTubeDetails.watchUrlOf("https://www.facebook.com/reel/1656853761974715/"))
    }

    @Test
    fun `a video yt-dlp let go unread says why`() {
        assertEquals("NWANo9BRz6Y: has already been recorded in the archive, skipping", skipReasonOf("null\n[download] NWANo9BRz6Y: has already been recorded in the archive, skipping"))
        assertNull(skipReasonOf("null"))
    }
}
