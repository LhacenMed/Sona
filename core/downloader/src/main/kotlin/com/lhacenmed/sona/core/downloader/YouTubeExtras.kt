package com.lhacenmed.sona.core.downloader

import org.json.JSONArray
import org.json.JSONObject

/**
 * What YouTube's `next` answer - the watch page's data - holds that yt-dlp writes into a video's read: its Most replayed
 * graph, and its chapters, with the description's as the last place they are looked for. Read where yt-dlp reads them, into
 * the shape it gives them, so a read completed with them is yt-dlp's own.
 */
internal object YouTubeExtras {

    /** [next]'s Most replayed graph: each span's start and end, in seconds, and how replayed it is - null with none. */
    fun heatmapOf(next: JSONObject): JSONArray? {
        val mutations = next.path("frameworkUpdates", "entityBatchUpdate").optJSONArray("mutations") ?: return null
        val markers = mutations.objects()
            .map { it.path("payload", "macroMarkersListEntity", "markersList") }
            .firstOrNull { it.optString("markerType") == "MARKER_TYPE_HEATMAP" }
            ?.optJSONArray("markers") ?: return null
        return JSONArray(
            markers.objects().map { marker ->
                val start = marker.optString("startMillis").toLongOrNull() ?: 0L
                val length = marker.optString("durationMillis").toLongOrNull() ?: 0L
                JSONObject()
                    .put("start_time", start / 1000.0)
                    .put("end_time", (start + length) / 1000.0)
                    .put("value", marker.optDouble("intensityScoreNormalized"))
            },
        ).takeIf { it.length() > 0 }
    }

    /**
     * The chapters of a video [durationSeconds] long - each its start, end and title: the player bar's in [next], else its
     * chapters panel's, else those its [description] lists. Null with none.
     */
    fun chaptersOf(next: JSONObject?, description: String?, durationSeconds: Double): JSONArray? {
        if (durationSeconds <= 0) return null
        val starts = next?.let(::playerBarChapters)?.takeIf { it.isNotEmpty() }
            ?: next?.let(::panelChapters)?.takeIf { it.isNotEmpty() }
            ?: description?.let(::descriptionChapters)
            ?: return null
        // As yt-dlp keeps them: in order, within the video.
        val kept = starts.sortedBy { it.first }.fold(mutableListOf<Pair<Double, String>>()) { chapters, chapter ->
            if (chapter.first in (chapters.lastOrNull()?.first ?: 0.0)..durationSeconds) chapters += chapter
            chapters
        }
        if (kept.isEmpty()) return null
        return JSONArray(
            kept.mapIndexed { index, (start, title) ->
                JSONObject().put("start_time", start).put("end_time", kept.getOrNull(index + 1)?.first ?: durationSeconds).put("title", title)
            },
        )
    }

    private fun playerBarChapters(next: JSONObject): List<Pair<Double, String>> =
        next.path("playerOverlays", "playerOverlayRenderer", "decoratedPlayerBarRenderer", "decoratedPlayerBarRenderer", "playerBar", "chapteredPlayerBarRenderer")
            .optJSONArray("chapters")?.objects()?.mapNotNull { chapter ->
                val renderer = chapter.optJSONObject("chapterRenderer") ?: return@mapNotNull null
                val start = renderer.optDouble("timeRangeStartMillis").takeUnless(Double::isNaN) ?: return@mapNotNull null
                start / 1000 to textOf(renderer.optJSONObject("title")).orEmpty()
            }.orEmpty()

    private fun panelChapters(next: JSONObject): List<Pair<Double, String>> =
        next.optJSONArray("engagementPanels")?.objects().orEmpty().asSequence()
            .mapNotNull { it.path("engagementPanelSectionListRenderer", "content", "macroMarkersListRenderer").optJSONArray("contents") }
            .map { contents ->
                contents.objects().mapNotNull { item ->
                    val renderer = item.optJSONObject("macroMarkersListItemRenderer") ?: return@mapNotNull null
                    val start = textOf(renderer.optJSONObject("timeDescription"))?.let(::secondsOf) ?: return@mapNotNull null
                    start to textOf(renderer.optJSONObject("title")).orEmpty()
                }
            }
            .firstOrNull { it.isNotEmpty() }.orEmpty()

    /** "0:00 Intro" lines, or "Intro 0:00" ones - yt-dlp's two ways of reading them. */
    private fun descriptionChapters(description: String): List<Pair<Double, String>>? {
        val timeFirst = Regex("""(?m)^\s*($Time)\b\W*\s(.+?)\s*$""").findAll(description).map { secondsOf(it.groupValues[1]) to it.groupValues[2] }.toList()
        if (timeFirst.isNotEmpty()) return timeFirst.mapNotNull { (start, title) -> start?.let { it to title } }
        return Regex("""(?m)^\s*(.+?)\b\W*\s($Time)\s*$""").findAll(description)
            .mapNotNull { match -> secondsOf(match.groupValues[2])?.let { it to match.groupValues[1] } }.toList().ifEmpty { null }
    }

    private const val Time = """(?:\d+:)?\d{1,2}:\d{2}"""

    /** "1:02:03" in seconds. */
    private fun secondsOf(time: String): Double? =
        time.trim().split(':').map { it.toIntOrNull() ?: return null }.fold(0.0) { total, part -> total * 60 + part }

    /** A text as YouTube writes one: whole, or in runs. */
    private fun textOf(text: JSONObject?): String? =
        text?.optString("simpleText")?.takeIf(String::isNotEmpty)
            ?: text?.optJSONArray("runs")?.objects()?.joinToString("") { it.optString("text") }?.takeIf(String::isNotEmpty)

    private fun JSONObject.path(vararg names: String): JSONObject = names.fold(this) { json, name -> json.optJSONObject(name) ?: JSONObject() }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { optJSONObject(it) }.filterNotNull()
}
