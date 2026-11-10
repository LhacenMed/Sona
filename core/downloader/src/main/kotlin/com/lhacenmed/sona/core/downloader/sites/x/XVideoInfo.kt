package com.lhacenmed.sona.core.downloader.sites.x

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.text
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import com.lhacenmed.sona.core.downloader.sites.wholeNumber
import org.json.JSONObject

/**
 * A post's syndication JSON as yt-dlp's info JSON, its fields and format ids as yt-dlp's twitter extractor writes them: each
 * mp4 on video.twimg.com a row, `http-<kbps>`; its HLS rows are yt-dlp's alone.
 *
 * Null where yt-dlp is left to read the post: no video of its own, a card, a quoted or reposted post - whose media yt-dlp
 * adds - a possibly sensitive post, an unknown media or variant shape, ids not exact and unique, or a link whose
 * `/photo|video/<n>` picks anything but its first video. Of several videos the first is read, titled ` #1`, as yt-dlp reads it.
 */
internal object XVideoInfo {
    private val declined = listOf("card", "quoted_tweet", "quoted_status", "retweeted_status", "retweeted_tweet")
    private val videoTypes = setOf("video", "animated_gif")
    private val digits = Regex("[0-9]{1,25}")
    private val sizeName = Regex("[a-z0-9_]{1,32}")
    private val dimensions = Regex("/(\\d+)x(\\d+)/")
    // \p{Z} and NEL stand in for the Unicode classes Android's regex flags refuse.
    private val link = Regex("[\\s\\p{Z}\\u0085]+(https?://[^ ]+)")

    fun build(root: JSONObject, postId: String, mediaIndex: Int?, webpageUrl: String): String? {
        if (root.opt("__typename") != "Tweet" || root.opt("id_str") != postId) return null
        if (declined.any { root.has(it) && !root.isNull(it) }) return null
        // yt-dlp marks these 18+: anything but an explicit false is its call.
        if (root.has("possibly_sensitive") && !root.isNull("possibly_sensitive") && root.opt("possibly_sensitive") != false) return null
        val media = root.optJSONArray("mediaDetails") ?: return null
        val items = (0 until media.length()).map { media.opt(it) }
        val videos = items.filter { (it as? JSONObject)?.opt("type") != "photo" }
        if (videos.any { it !is JSONObject || it.opt("type") !in videoTypes }) return null
        val video = videos.firstOrNull() as? JSONObject ?: return null
        val gif = video.opt("type") == "animated_gif"
        // twitter.py with an index: `mediaDetails[n-1]`, photos counted, and it must be a video - a GIF is refused.
        if (mediaIndex != null && (items.getOrNull(mediaIndex - 1) !== video || gif)) return null
        val variants = video.optJSONObject("video_info")?.optJSONArray("variants") ?: return null
        val progressive = (0 until variants.length()).map { variants.opt(it) }.filter { (it as? JSONObject)?.text("url")?.contains(".m3u8") != true }
        // A GIF is one file without a bitrate, yt-dlp's one `http` row; any other GIF shape is yt-dlp's.
        if (gif && (progressive.size != 1 || bitrate(progressive.single() as? JSONObject ?: return null) != 0L)) return null
        val original = video.optJSONObject("original_info")
        val formats = progressive.map { variant ->
            val row = variant as? JSONObject ?: return null
            val url = row.text("url") ?: return null
            if (row.opt("content_type") != "video/mp4" || !isXVideo(url)) return null
            val tbr = (bitrate(row) ?: return null) / 1000
            val size = dimensions.find(url)
            val width = size?.groupValues?.get(1)?.toIntOrNull() ?: original?.opt("width")?.wholeNumber()?.toInt()?.takeIf { gif }
            val height = size?.groupValues?.get(2)?.toIntOrNull() ?: original?.opt("height")?.wholeNumber()?.toInt()?.takeIf { gif }
            VideoFormat(
                id = if (tbr > 0) "http-$tbr" else "http",
                url = url,
                width = width,
                height = height,
                videoCodec = "h264",
                audioCodec = if (gif) "none" else "aac",
                bitrateKbps = tbr.takeIf { it > 0 }?.toDouble(),
            )
        }
        if (formats.isEmpty()) return null
        val user = root.optJSONObject("user")
        return videoInfoJson(
            VideoPost(
                id = video.text("id_str")?.takeIf(digits::matches) ?: postId,
                title = title(root) + if (videos.size > 1) " #1" else "",
                uploader = user?.text("name"),
                thumbnail = thumbnail(video),
                durationSeconds = (video.optJSONObject("video_info")?.opt("duration_millis") as? Number)?.toDouble()?.div(1000),
                webpageUrl = webpageUrl,
                extractorKey = "Twitter",
                formats = formats,
            ),
        ) {
            put("display_id", postId)
            root.text("text")?.let { put("description", it.replace('\n', ' ')) }
            user?.text("screen_name")?.let { put("uploader_id", it) }
            put("extractor", "twitter")
        }
    }

    /** yt-dlp's `bitrate`, else `bit_rate`, skipping a missing or zero one - null for a value that is no bitrate. */
    private fun bitrate(variant: JSONObject): Long? {
        for (key in listOf("bitrate", "bit_rate")) {
            if (!variant.has(key) || variant.isNull(key)) continue
            val value = variant.opt(key)
            if (value !is Int && value !is Long) return null
            val rate = (value as Number).toLong()
            if (rate < 0) return null
            if (rate > 0) return rate
        }
        return 0
    }

    private fun isXVideo(url: String): Boolean {
        val parsed = HttpsUrl.parse(url) ?: return false
        return parsed.host == "video.twimg.com" && parsed.encodedPath.endsWith(".mp4") && !parsed.encodedPath.contains("..")
    }

    /** twitter.py: the text, its newlines as spaces and its links taken out, cut at 72 code points, after the author's name. */
    private fun title(root: JSONObject): String {
        val text = link.replace(root.text("text").orEmpty().replace('\n', ' '), "")
        val cut = if (text.codePointCount(0, text.length) <= 72) text else text.substring(0, text.offsetByCodePoints(0, 69)) + "..."
        val author = root.optJSONObject("user")?.text("name")
        return if (author != null) "$author - $cut" else cut
    }

    /** twitter.py's largest picture of the video: its poster, at the largest size it names, `orig` among them. */
    private fun thumbnail(video: JSONObject): String? {
        val base = video.text("media_url_https")?.takeIf { url ->
            HttpsUrl.parse(url)?.let { it.host == "pbs.twimg.com" && it.encodedQuery == null } == true
        } ?: return null
        val sizes = video.optJSONObject("sizes")
        val named = sizes?.keys()?.asSequence()?.mapNotNull { name -> sizes.optJSONObject(name)?.let { name to it } }?.toList().orEmpty() +
            listOfNotNull(video.optJSONObject("original_info")?.let { "orig" to it })
        val best = named.filter { sizeName.matches(it.first) }.maxWithOrNull(compareBy<Pair<String, JSONObject>>(
            { side(it.second, "w", "width") }, { side(it.second, "h", "height") }, { it.first },
        )) ?: return null
        return "$base?name=${best.first}"
    }

    private fun side(size: JSONObject, short: String, long: String): Long =
        (size.opt(short) ?: size.opt(long))?.wholeNumber() ?: 0
}
