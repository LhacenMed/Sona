package com.lhacenmed.sona.core.downloader

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerHotspotsTest {

    private val views = listOf(298860, 61637, 49999, 43246, 40885, 36471, 30967, 29053, 28944, 28172, 27123, 25905)

    private fun pageWith(hotspots: String) = """<script>var flashvars_1 = {"video_duration":60,"hotspots":[$hotspots],"actionTags":""};</script>"""

    @Test
    fun `a player's hotspots are its views per equal slice of the video, the first held to the next`() {
        val graph = JSONArray(heatmapJsonOf(pageWith(views.joinToString(",")), durationSeconds = 60)!!)
        assertEquals(views.size, graph.length())
        assertEquals(0.0, graph.getJSONObject(0).getDouble("start_time"), 0.0)
        assertEquals(5.0, graph.getJSONObject(0).getDouble("end_time"), 1e-9)
        assertEquals(61637.0, graph.getJSONObject(0).getDouble("value"), 0.0)
        assertEquals(61637.0, graph.getJSONObject(1).getDouble("value"), 0.0)
        assertEquals(60.0, graph.getJSONObject(views.size - 1).getDouble("end_time"), 1e-9)
    }

    @Test
    fun `hotspots written as strings are read as their numbers`() {
        val graph = JSONArray(heatmapJsonOf(pageWith(views.joinToString(",") { "\"$it\"" }), durationSeconds = 60)!!)
        assertEquals(25905.0, graph.getJSONObject(views.size - 1).getDouble("value"), 0.0)
    }

    @Test
    fun `a page without a graph, or with too little of one, has none`() {
        assertNull(heatmapJsonOf("<html>hotspots: { data: flashvars.hotspots }</html>", durationSeconds = 60))
        assertNull(heatmapJsonOf(pageWith(""), durationSeconds = 60))
        assertNull(heatmapJsonOf(pageWith("1,2,3"), durationSeconds = 60))
        assertNull(heatmapJsonOf(pageWith(views.joinToString(",") + ",x"), durationSeconds = 60))
        assertNull(heatmapJsonOf(pageWith(List(12) { 0 }.joinToString(",")), durationSeconds = 60))
        assertNull(heatmapJsonOf(pageWith(views.joinToString(",")), durationSeconds = 0))
    }
}
