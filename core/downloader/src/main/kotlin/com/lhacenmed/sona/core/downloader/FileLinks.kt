package com.lhacenmed.sona.core.downloader

import android.webkit.MimeTypeMap
import com.lhacenmed.sona.core.downloader.transfer.FileRequestHeaders
import com.lhacenmed.sona.core.downloader.transfer.probe
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * A link that serves a file rather than a page - a song, a video, an archive, anything - however its address reads: told by
 * what it serves, as AB Download Manager tells it, and downloaded as it is served, by [DirectTransfer]. What yt-dlp's generic
 * reader would find of it, found as a file manager finds it: named as its server names it, its size known.
 */
internal object FileLinks {
    /**
     * [url], read as the file it serves - in yt-dlp's shape, one format, the file itself: a song's or a video's taken to hold
     * its sound or picture; any other neither, and marked a file - see [MediaInfo.isFile]. Null for what yt-dlp reads: a page, a
     * feed, a stream's playlist. Throws where the link fails. Asked, and fetched, with [headers] over Sona's own - the browser's
     * it was found in, where it was.
     */
    fun read(url: String, client: OkHttpClient, headers: Map<String, String> = emptyMap()): String? {
        val requestHeaders = FileRequestHeaders + headers
        val served = client.probe(url, requestHeaders)
        if (served.isForDownloader) return null
        val link = url.toHttpUrlOrNull()
        val name = served.name ?: link?.host ?: "download"
        val extension = name.substringAfterLast('.', "").lowercase()
        // What it holds is what its name says - made to agree with what it was served as - and unknown in detail until fetched.
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
        val isVideo = type.startsWith("video/")
        val isMedia = isVideo || type.startsWith("audio/")
        val title = name.substringBeforeLast('.').ifEmpty { name }
        val format = JSONObject()
            .put("format_id", "file")
            .put("url", url)
            .put("protocol", link?.scheme ?: "https")
            .put("ext", extension)
            .put("vcodec", if (isVideo) "unknown" else "none")
            .put("acodec", if (isMedia) "unknown" else "none")
            .put("http_headers", JSONObject(requestHeaders))
            .apply { served.totalBytes?.let { put("filesize", it) } }
        return JSONObject()
            .put("_type", if (isMedia) "video" else FILE_TYPE)
            // Handed to yt-dlp where a song or a video is to be converted, which reads nothing without one.
            .put("id", title.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifEmpty { "file" })
            .put("title", title)
            .put("webpage_url", url)
            .put("extractor_key", link?.host?.removePrefix("www."))
            .put("formats", JSONArray().put(format))
            .toString()
    }
}
