package com.lhacenmed.sona.core.downloader

import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import com.lhacenmed.sona.core.model.VideoChapter
import com.lhacenmed.sona.core.model.VideoStats
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/** What a downloaded file carries of its site beyond what its format holds by itself - see [EmbeddedExtras]. */
class DownloadedExtras(
    /** The site's Most replayed graph, from 0 to 1 along the video - null with none. */
    val heatmap: FloatArray?,
    val chapters: List<VideoChapter>,
    /** What its site counted of it as it was downloaded - null where it said nothing. */
    val stats: VideoStats?,
)

/** yt-dlp's fields for when a video was uploaded and how often watched and liked, as every site's extractor names them. */
internal const val VideoStatsFields = "upload_date,timestamp,view_count,like_count"

/** Those of [VideoStatsFields] [json] has, as yt-dlp gave them - null where it has none. */
internal fun videoStatsJsonOf(json: JSONObject): String? {
    val stats = JSONObject()
    VideoStatsFields.split(',').forEach { field -> json.opt(field)?.takeUnless { it == JSONObject.NULL }?.let { stats.put(field, it) } }
    return stats.takeIf { it.length() > 0 }?.toString()
}

/**
 * What a downloaded file carries of its site, in the file itself - so it goes wherever the file goes, and any app can read it:
 * its captions as a track of its own and its chapters as its chapter list, written by yt-dlp; and, as tags of their own -
 * written by TagLib, as any tag editor reads them - the site's Most replayed graph and its counts of the video, which no
 * format has a place for, and Sona's copy of the chapters, read as tags are rather than out of each format's own chapter
 * list. Each tag holds what yt-dlp gave, as it gave it.
 *
 * The library reads them back into its index - see the scanner's `VideoExtras` - and the player reads a Private Folder video's
 * from its file itself, so the file is all there is to keep, wherever it is moved.
 */
object EmbeddedExtras {
    private const val HEATMAP = "HEATMAP"
    private const val CHAPTERS = "CHAPTERS"
    private const val STATS = "VIDEO_STATS"

    /** Writes yt-dlp's [heatmapJson], [chaptersJson] and [statsJson] into [file]'s tags, beside what it has - nothing, with none. */
    fun write(file: File, heatmapJson: String?, chaptersJson: String?, statsJson: String?) {
        if (heatmapJson == null && chaptersJson == null && statsJson == null) return
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
            // TagLib takes the descriptor it is handed as its own and closes it: it is given a duplicate.
            val properties = TagLib.getMetadata(descriptor.dup().detachFd(), readPictures = false)?.propertyMap ?: return
            heatmapJson?.let { properties[HEATMAP] = arrayOf(it) }
            chaptersJson?.let { properties[CHAPTERS] = arrayOf(it) }
            statsJson?.let { properties[STATS] = arrayOf(it) }
            TagLib.savePropertyMap(descriptor.dup().detachFd(), properties)
        }
    }

    /** What [file] carries - null for a file that carries none of it, or cannot be read. */
    fun read(file: File): DownloadedExtras? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            val properties = TagLib.getMetadata(descriptor.dup().detachFd(), readPictures = false)?.propertyMap ?: return null
            val heatmap = properties[HEATMAP]?.firstOrNull()?.let { heightsOf(JSONArray(it)) }
            val chapters = properties[CHAPTERS]?.firstOrNull()?.let { chaptersOf(JSONArray(it)) }.orEmpty()
            val stats = properties[STATS]?.firstOrNull()?.let { statsOf(JSONObject(it)) }
            if (heatmap == null && chapters.isEmpty() && stats == null) null else DownloadedExtras(heatmap, chapters, stats)
        }
    }.getOrNull()

    /**
     * Whether any of [file]'s tags holds [text] - a video's id in the link yt-dlp, Seal or YTDLnis wrote into it as they saved
     * it ("purl", "comment"). False for a file that cannot be read.
     */
    fun mentions(file: File, text: String): Boolean = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            val properties = TagLib.getMetadata(descriptor.dup().detachFd(), readPictures = false)?.propertyMap ?: return false
            properties.values.any { values -> values.any { it.contains(text) } }
        }
    }.getOrDefault(false)

    /** A site's Most replayed markers - spans of the video, each with how replayed it is - as heights along it, from 0 to 1. */
    private fun heightsOf(markers: JSONArray): FloatArray? {
        val values = FloatArray(markers.length()) { markers.optJSONObject(it)?.optDouble("value")?.toFloat()?.takeUnless(Float::isNaN) ?: 0f }
        val peak = values.maxOrNull()?.takeIf { it > 0f } ?: return null
        return FloatArray(values.size) { values[it] / peak }
    }

    /**
     * A site's counts of a video: when it was uploaded - to the second where its site says, else the day, as UTC's midnight -
     * and how often watched and liked. Null where it says none of them.
     */
    internal fun statsOf(json: JSONObject): VideoStats? {
        val uploadedAtMs = json.longOrNull("timestamp")?.times(1_000)
            ?: json.optString("upload_date").takeIf { it.length == 8 }?.let { day ->
                runCatching { LocalDate.parse(day, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            }
        return VideoStats(uploadedAtMs, json.longOrNull("view_count"), json.longOrNull("like_count"))
            .takeIf { it.uploadedAtMs != null || it.viewCount != null || it.likeCount != null }
    }

    /** [key]'s number, where it holds one. */
    private fun JSONObject.longOrNull(key: String): Long? = optDouble(key).takeUnless(Double::isNaN)?.toLong()

    /** A site's chapters - each with where it starts, in seconds, and its title - in the order they come. */
    private fun chaptersOf(chapters: JSONArray): List<VideoChapter> = List(chapters.length()) { chapters.optJSONObject(it) }.mapNotNull { chapter ->
        val start = chapter?.optDouble("start_time")?.takeUnless(Double::isNaN) ?: return@mapNotNull null
        VideoChapter((start * 1_000).toLong(), chapter.optString("title"))
    }.sortedBy { it.startMs }
}
