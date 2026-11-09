package com.lhacenmed.sona.core.downloader

import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import com.lhacenmed.sona.core.model.VideoChapter
import java.io.File
import org.json.JSONArray

/** What a downloaded file carries of its site beyond what its format holds by itself - see [EmbeddedExtras]. */
class DownloadedExtras(
    /** The site's Most replayed graph, from 0 to 1 along the video - null with none. */
    val heatmap: FloatArray?,
    val chapters: List<VideoChapter>,
)

/**
 * What a downloaded file carries of its site, in the file itself - so it goes wherever the file goes, and any app can read it:
 * its captions as a track of its own and its chapters as its chapter list, written by yt-dlp; and, as tags of their own -
 * written by TagLib, as any tag editor reads them - the site's Most replayed graph, which no format has a place for, and
 * Sona's copy of the chapters, read as tags are rather than out of each format's own chapter list. Each tag holds what
 * yt-dlp gave, as it gave it.
 *
 * The library reads them back into its index - see the scanner's `VideoExtras` - so the file is all there is to keep.
 */
object EmbeddedExtras {
    private const val HEATMAP = "HEATMAP"
    private const val CHAPTERS = "CHAPTERS"

    /** Writes yt-dlp's [heatmapJson] and [chaptersJson] into [file]'s tags, beside what it has - nothing, with neither. */
    fun write(file: File, heatmapJson: String?, chaptersJson: String?) {
        if (heatmapJson == null && chaptersJson == null) return
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
            // TagLib takes the descriptor it is handed as its own and closes it: it is given a duplicate.
            val properties = TagLib.getMetadata(descriptor.dup().detachFd(), readPictures = false)?.propertyMap ?: return
            heatmapJson?.let { properties[HEATMAP] = arrayOf(it) }
            chaptersJson?.let { properties[CHAPTERS] = arrayOf(it) }
            TagLib.savePropertyMap(descriptor.dup().detachFd(), properties)
        }
    }

    /** What [file] carries - null for a file that carries neither, or cannot be read. */
    fun read(file: File): DownloadedExtras? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            val properties = TagLib.getMetadata(descriptor.dup().detachFd(), readPictures = false)?.propertyMap ?: return null
            val heatmap = properties[HEATMAP]?.firstOrNull()?.let { heightsOf(JSONArray(it)) }
            val chapters = properties[CHAPTERS]?.firstOrNull()?.let { chaptersOf(JSONArray(it)) }.orEmpty()
            if (heatmap == null && chapters.isEmpty()) null else DownloadedExtras(heatmap, chapters)
        }
    }.getOrNull()

    /** A site's Most replayed markers - spans of the video, each with how replayed it is - as heights along it, from 0 to 1. */
    private fun heightsOf(markers: JSONArray): FloatArray? {
        val values = FloatArray(markers.length()) { markers.optJSONObject(it)?.optDouble("value")?.toFloat()?.takeUnless(Float::isNaN) ?: 0f }
        val peak = values.maxOrNull()?.takeIf { it > 0f } ?: return null
        return FloatArray(values.size) { values[it] / peak }
    }

    /** A site's chapters - each with where it starts, in seconds, and its title - in the order they come. */
    private fun chaptersOf(chapters: JSONArray): List<VideoChapter> = List(chapters.length()) { chapters.optJSONObject(it) }.mapNotNull { chapter ->
        val start = chapter?.optDouble("start_time")?.takeUnless(Double::isNaN) ?: return@mapNotNull null
        VideoChapter((start * 1_000).toLong(), chapter.optString("title"))
    }.sortedBy { it.startMs }
}
