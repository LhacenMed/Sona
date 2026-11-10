package com.lhacenmed.sona.core.downloader.sites.threads

import com.lhacenmed.sona.core.downloader.objects
import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.firstLine
import com.lhacenmed.sona.core.downloader.sites.text
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import com.lhacenmed.sona.core.downloader.sites.wholeNumber
import java.math.BigInteger
import org.json.JSONObject
import org.json.JSONTokener

/** A logged-out post page runs to about a megabyte. */
private const val MaxBytes = 3 * 512 * 1024

/** A public Threads post link, `threads.com|threads.net/@<user>/post/<code>`. A profile or a reply list is no post. */
internal class ThreadsPostIdentity private constructor(val user: String, val code: String) {
    /** The one page read of it: www.threads.com, no query. */
    val fetchUrl: String get() = "https://www.threads.com/@$user/post/$code"

    companion object {
        private val hosts = setOf("threads.com", "www.threads.com", "threads.net", "www.threads.net")
        private val path = Regex("/(?:@|%40)([A-Za-z0-9._]{1,30})/post/([A-Za-z0-9_-]{1,32})(?:/media)?/?")
        private const val Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        fun parse(raw: String): ThreadsPostIdentity? {
            val url = HttpsUrl.parse(raw) ?: return null
            if (url.host !in hosts) return null
            val match = path.matchEntire(url.encodedPath) ?: return null
            val user = match.groupValues[1]
            if (user.startsWith('.') || user.endsWith('.')) return null
            return ThreadsPostIdentity(user, match.groupValues[2])
        }

        /** The media id [code] is in Instagram's shortcode alphabet - null where it is not that alphabet. */
        fun mediaId(code: String): String? {
            var number = BigInteger.ZERO
            val base = BigInteger.valueOf(64)
            for (char in code) {
                val index = Alphabet.indexOf(char)
                if (index < 0) return null
                number = number.multiply(base).add(BigInteger.valueOf(index.toLong()))
            }
            return number.toString()
        }
    }
}

/**
 * Threads, read from a post's logged-out page: its TargetQuery preloader names the post, and of a video, its one largest
 * progressive MP4 of `video_versions` - the DASH manifest is not read. A carousel is read as its first video. A post with
 * no video is not read.
 */
internal object ThreadsReader : SiteReader {
    override fun accepts(url: String): Boolean = ThreadsPostIdentity.parse(url) != null

    override fun read(url: String, http: SiteHttp): String? {
        val identity = ThreadsPostIdentity.parse(url) ?: return null
        val page = http.get(identity.fetchUrl, MaxBytes, SiteHttp.ThreadsPage) ?: return null
        return parse(page.text, identity.code, identity.fetchUrl)
    }

    /** The video of the post [code] on [html] as info JSON - null where it holds none, or names another post. */
    fun parse(html: String, code: String, webpageUrl: String): String? {
        val root = preloader(html) ?: return null
        val media = root.optJSONObject("__bbox")?.optJSONObject("result")?.optJSONObject("data")?.optJSONObject("media") ?: return null
        if (!isSamePost(media, code)) return null
        val video = when (media.opt("media_type").wholeNumber()) {
            2L -> media
            8L -> media.optJSONArray("carousel_media")?.objects()?.firstOrNull { it.opt("media_type").wholeNumber() == 2L }
            else -> null
        } ?: return null
        val format = largest(video) ?: return null
        return videoInfoJson(
            VideoPost(
                id = code,
                title = firstLine(media.optJSONObject("caption")?.text("text")),
                uploader = media.optJSONObject("user")?.text("username"),
                // The post's own poster frame, so the video shows a picture.
                thumbnail = poster(video),
                durationSeconds = (video.opt("video_duration") as? Number)?.toDouble(),
                webpageUrl = webpageUrl,
                extractorKey = "Threads",
                formats = listOf(format),
            ),
        )
    }

    /** The largest of [node]'s `video_versions` by area - the first of it where sizes tie or are not said. */
    private fun largest(node: JSONObject): VideoFormat? {
        val rows = node.optJSONArray("video_versions")?.objects().orEmpty().mapNotNull { version ->
            val url = version.text("url")?.takeIf(::isVideo) ?: return@mapNotNull null
            val width = version.opt("width").wholeNumber()?.toInt()?.takeIf { it > 0 }
            val height = version.opt("height").wholeNumber()?.toInt()?.takeIf { it > 0 }
            Row(if (width != null && height != null) width.toLong() * height else -1L, url, width, height, version.opt("filesize").wholeNumber())
        }
        val chosen = rows.firstOrNull { row -> row.area == rows.maxOf { it.area } } ?: return null
        // A version's own size wins; the original's stands in only where the version says neither side.
        val bothSaid = chosen.width != null && chosen.height != null
        val neitherSaid = chosen.width == null && chosen.height == null
        val width = if (bothSaid) chosen.width else if (neitherSaid) node.opt("original_width").wholeNumber()?.toInt() else null
        val height = if (bothSaid) chosen.height else if (neitherSaid) node.opt("original_height").wholeNumber()?.toInt() else null
        return VideoFormat(
            id = "progressive",
            url = chosen.url,
            width = width,
            height = height,
            videoCodec = "h264",
            // A silent progressive file has no sound track; one that says nothing has one.
            audioCodec = if (node.opt("has_audio") == false) "none" else "aac",
            sizeBytes = chosen.bytes,
        )
    }

    private class Row(val area: Long, val url: String, val width: Int?, val height: Int?, val bytes: Long?)

    /** The widest picture [node] names on Threads' image hosts. */
    private fun poster(node: JSONObject): String? = node.optJSONObject("image_versions2")?.optJSONArray("candidates")?.objects().orEmpty()
        .mapNotNull { candidate ->
            candidate.text("url")?.takeIf { url -> HttpsUrl.parse(url)?.let { isThreadsCdn(it.host) } == true }
                ?.let { it to (candidate.opt("width").wholeNumber() ?: 0) }
        }
        .maxByOrNull { it.second }?.first

    private fun isSamePost(media: JSONObject, code: String): Boolean {
        val posted = media.text("code")
        val pk = media.opt("pk")?.takeIf { it != JSONObject.NULL }?.toString()?.takeIf { it.isNotEmpty() }
        if (posted == null && pk == null) return false
        if (posted != null && posted != code) return false
        return pk == null || pk == ThreadsPostIdentity.mediaId(code)
    }

    private fun isVideo(raw: String): Boolean {
        val url = HttpsUrl.parse(raw) ?: return false
        return isThreadsCdn(url.host) && !url.encodedPath.contains("..") && Regex("/[A-Za-z0-9_./%-]+\\.mp4").matches(url.encodedPath)
    }

    private fun isThreadsCdn(host: String): Boolean = host.endsWith(".cdninstagram.com") || host.endsWith(".fbcdn.net")

    /** The TargetQuery preloader's `__bbox` object, from the page that holds it. */
    private fun preloader(html: String): JSONObject? {
        var from = 0
        while (true) {
            val at = html.indexOf(Preloader, from)
            if (at < 0) return null
            var end = at + Preloader.length
            while (end < html.length && html[end].let { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) end++
            if (end > at + Preloader.length && end - at - Preloader.length <= 64 && html.startsWith(Bbox, end)) {
                return runCatching { JSONTokener(html.substring(end + 2)).nextValue() as? JSONObject }.getOrNull()
            }
            from = at + 1
        }
    }

    private const val Preloader = "adp_BarcelonaPostPageTargetQueryRelayPreloader_"

    /** `",{"__bbox":` - its `{` two characters in. */
    private const val Bbox = "\",{\"__bbox\":"
}
