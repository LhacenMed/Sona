package com.lhacenmed.sona.core.downloader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeExtrasTest {

    @Test
    fun `the Most replayed graph is read from the watch page's markers, in seconds`() {
        val next = JSONObject(
            """{"frameworkUpdates":{"entityBatchUpdate":{"mutations":[
              {"payload":{"macroMarkersListEntity":{"markersList":{"markerType":"MARKER_TYPE_CHAPTERS","markers":[]}}}},
              {"payload":{"macroMarkersListEntity":{"markersList":{"markerType":"MARKER_TYPE_HEATMAP","markers":[
                {"startMillis":"0","durationMillis":"9520","intensityScoreNormalized":1},
                {"startMillis":"9520","durationMillis":"9520","intensityScoreNormalized":0.25}]}}}}]}}}""",
        )
        val graph = YouTubeExtras.heatmapOf(next)!!
        assertEquals(2, graph.length())
        assertEquals(9.52, graph.getJSONObject(1).getDouble("start_time"), 1e-9)
        assertEquals(19.04, graph.getJSONObject(1).getDouble("end_time"), 1e-9)
        assertEquals(0.25, graph.getJSONObject(1).getDouble("value"), 0.0)
        assertNull(YouTubeExtras.heatmapOf(JSONObject("{}")))
    }

    @Test
    fun `chapters come from the player bar before the description, each ending where the next starts`() {
        val next = JSONObject(
            """{"playerOverlays":{"playerOverlayRenderer":{"decoratedPlayerBarRenderer":{"decoratedPlayerBarRenderer":{"playerBar":
              {"chapteredPlayerBarRenderer":{"chapters":[
                {"chapterRenderer":{"title":{"simpleText":"Intro"},"timeRangeStartMillis":0}},
                {"chapterRenderer":{"title":{"simpleText":"Talk"},"timeRangeStartMillis":54000}}]}}}}}}}""",
        )
        val chapters = YouTubeExtras.chaptersOf(next, "0:00 Other\n1:00 Ignored", durationSeconds = 100.0)!!
        assertEquals("Talk", chapters.getJSONObject(1).getString("title"))
        assertEquals(54.0, chapters.getJSONObject(0).getDouble("end_time"), 0.0)
        assertEquals(100.0, chapters.getJSONObject(1).getDouble("end_time"), 0.0)
    }

    @Test
    fun `without the watch page, chapters are the description's, past the video's end left out`() {
        val description = "Links below\n0:00 Introduction\n0:54 - Interview\n1:02:03 Too late"
        val chapters = YouTubeExtras.chaptersOf(null, description, durationSeconds = 952.0)!!
        assertEquals(2, chapters.length())
        assertEquals("Interview", chapters.getJSONObject(1).getString("title"))
        assertEquals(54.0, chapters.getJSONObject(1).getDouble("start_time"), 0.0)
        assertNull(YouTubeExtras.chaptersOf(null, "No times here", durationSeconds = 952.0))
    }
}
