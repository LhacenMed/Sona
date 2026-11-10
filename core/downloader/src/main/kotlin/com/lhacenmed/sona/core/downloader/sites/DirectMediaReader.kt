package com.lhacenmed.sona.core.downloader.sites

import java.net.URLDecoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** The kinds of file a link straight to one is read as, by its extension: a picture with its sound, or a sound alone. */
private val VideoExtensions = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp")
private val AudioExtensions = setOf("m4a", "mp3", "flac", "ogg", "opus", "wav", "aac")

/**
 * A link straight to a media file - `…/song.mp3`, `…/clip.mp4` - read from the link alone, with no request: the file is its
 * one format, its name its title. What yt-dlp's generic reader would find, found at once, and downloaded without the
 * downloader where the file needs nothing done to it.
 */
internal object DirectMediaReader : SiteReader {
    override fun accepts(url: String): Boolean = extensionOf(url) != null

    override fun read(url: String, http: SiteHttp): String? {
        val link = url.toHttpUrlOrNull() ?: return null
        val extension = extensionOf(url) ?: return null
        val name = URLDecoder.decode(link.pathSegments.last(), "UTF-8").substringBeforeLast('.')
        val isVideo = extension in VideoExtensions
        val format = JSONObject()
            .put("format_id", "file")
            .put("url", url)
            .put("protocol", link.scheme)
            .put("ext", extension)
            // What it holds is unknown until it is fetched: a video's file is taken to carry its sound.
            .put("vcodec", if (isVideo) "unknown" else "none")
            .put("acodec", "unknown")
        return JSONObject()
            .put("_type", "video")
            .put("id", name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifEmpty { "file" })
            .put("title", name.ifBlank { link.host })
            .put("webpage_url", url)
            .put("extractor_key", link.host.removePrefix("www."))
            .put("formats", JSONArray().put(format))
            .toString()
    }

    /** The media extension [url]'s path ends in - null for a page, or a file of no other kind. */
    private fun extensionOf(url: String): String? {
        val link = url.toHttpUrlOrNull() ?: return null
        val extension = link.pathSegments.lastOrNull()?.substringAfterLast('.', "")?.lowercase() ?: return null
        return extension.takeIf { it in VideoExtensions || it in AudioExtensions }
    }
}
