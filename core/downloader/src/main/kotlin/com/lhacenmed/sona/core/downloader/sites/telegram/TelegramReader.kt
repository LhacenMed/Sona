package com.lhacenmed.sona.core.downloader.sites.telegram

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.firstLine
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** A post's widget page is a few tens of kilobytes. */
private const val MaxBytes = 1024 * 1024

/**
 * A public channel's post link; a private `/c/` link, an invite, a share or a profile is not one. The web preview
 * (`t.me/s/<channel>/<id>`) and a forum topic's message (`t.me/<channel>/<topic>/<id>`) are that same post.
 */
internal class TelegramPostIdentity private constructor(val channel: String, val postId: String) {
    val postKey: String get() = "$channel/$postId"
    val fetchUrl: String get() = "https://t.me/$postKey?embed=1&mode=tme"

    companion object {
        private val hosts = setOf("t.me", "telegram.me")
        private val path = Regex("/(?:s/)?([A-Za-z][A-Za-z0-9_]{4,31})/(?:[1-9][0-9]{0,19}/)?([1-9][0-9]{0,19})/?")
        private val reserved = setOf("share", "proxy", "socks", "joinchat", "addstickers", "addemoji", "login", "invoice")

        fun parse(raw: String): TelegramPostIdentity? {
            val url = HttpsUrl.parse(raw) ?: return null
            if (url.host !in hosts) return null
            val match = path.matchEntire(url.encodedPath) ?: return null
            if (match.groupValues[1].lowercase() in reserved) return null
            return TelegramPostIdentity(match.groupValues[1], match.groupValues[2])
        }
    }
}

/**
 * Telegram, read from a public post's embed widget: its one video's file, as the widget plays it, as yt-dlp's telegram
 * extractor reads it. The widget names no size but the one it shows the video at, which stands for it. A post with no
 * video, or several, or one too big for the web to play, is not read.
 */
internal object TelegramReader : SiteReader {
    override fun accepts(url: String): Boolean = TelegramPostIdentity.parse(url) != null

    override fun read(url: String, http: SiteHttp): String? {
        val identity = TelegramPostIdentity.parse(url) ?: return null
        // A redirect is the widget's way of saying the post is not public.
        val page = http.get(identity.fetchUrl, MaxBytes, SiteHttp.BrowserPage) ?: return null
        return parse(page.text, identity.postKey, "https://t.me/${identity.postKey}")
    }

    /** The one video of the post [postKey] on [html] - null for anything else. */
    fun parse(html: String, postKey: String, webpageUrl: String): String? {
        val document = runCatching { Jsoup.parse(html) }.getOrNull() ?: return null
        if (document.selectFirst(".tgme_widget_message_error") != null) return null
        val message = document.select(".tgme_widget_message[data-post]").singleOrNull { it.attr("data-post").equals(postKey, ignoreCase = true) }
            ?: return null
        // Only the message's own media: not a link preview's, nor a reply's.
        fun own(element: Element): Boolean = element.closest(".tgme_widget_message") === message &&
            element.closest(".tgme_widget_message_link_preview, .tgme_widget_message_reply, .tgme_widget_message_reply_to") == null
        val video = message.select("video.tgme_widget_message_video").filter(::own).singleOrNull() ?: return null
        val url = video.attr("src").takeIf(::isVideo) ?: return null
        val player = video.closest(".tgme_widget_message_video_player")
        val wrap = player?.selectFirst(".tgme_widget_message_video_wrap")
        val width = wrap?.let { Regex("width\\s*:\\s*([0-9]+)px").find(it.attr("style"))?.groupValues?.get(1)?.toIntOrNull() }
        val ratio = wrap?.let { Regex("padding-top\\s*:\\s*([0-9.]+)%").find(it.attr("style"))?.groupValues?.get(1)?.toDoubleOrNull() }
        val height = if (width != null && ratio != null) Math.round(width * ratio / 100).toInt() else null
        val thumbnail = player?.selectFirst(".tgme_widget_message_video_thumb")?.attr("style")
            ?.let { Background.find(it)?.groupValues?.get(2) }?.takeIf { HttpsUrl.parse(it) != null }
        return videoInfoJson(
            VideoPost(
                id = postKey.replace('/', '-'),
                title = firstLine(message.selectFirst(".tgme_widget_message_text")?.wholeText()),
                uploader = message.selectFirst(".tgme_widget_message_owner_name")?.text()?.takeIf(String::isNotBlank),
                thumbnail = thumbnail,
                durationSeconds = player?.selectFirst(".message_video_duration")?.text()?.let(::seconds),
                webpageUrl = webpageUrl,
                extractorKey = "Telegram",
                formats = listOf(VideoFormat("video", url, width, height, videoCodec = "h264", audioCodec = "aac")),
            ),
        )
    }

    /** `1:02:03` or `0:13`, in seconds. */
    private fun seconds(text: String): Double? {
        val parts = text.trim().split(':').map { it.toIntOrNull() ?: return null }
        if (parts.size !in 2..3) return null
        return parts.fold(0.0) { total, part -> total * 60 + part }
    }

    private fun isVideo(raw: String): Boolean {
        val url = HttpsUrl.parse(raw) ?: return false
        return Regex("cdn[0-9]{1,5}\\.(?:telesco\\.pe|cdn-telegram\\.org)").matches(url.host) &&
            Regex("/file/[A-Za-z0-9_-]{1,2048}={0,2}(?:\\.mp4)?").matches(url.encodedPath)
    }

    private val Background = Regex("""background-image\s*:\s*url\(\s*(['"]?)(https://[^\s'"()]+)\1\s*\)""", RegexOption.IGNORE_CASE)
}
