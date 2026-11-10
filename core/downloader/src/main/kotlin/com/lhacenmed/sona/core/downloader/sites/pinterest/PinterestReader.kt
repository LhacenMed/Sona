package com.lhacenmed.sona.core.downloader.sites.pinterest

import com.lhacenmed.sona.core.downloader.objects
import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.keyList
import com.lhacenmed.sona.core.downloader.sites.ShortLinks
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.firstLine
import com.lhacenmed.sona.core.downloader.sites.text
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import com.lhacenmed.sona.core.downloader.sites.wholeNumber
import java.net.URLDecoder
import java.util.Base64
import okhttp3.HttpUrl
import org.json.JSONObject
import org.json.JSONTokener

/** A logged-out pin page runs to about a megabyte. */
private const val MaxBytes = 3 * 1024 * 1024

/**
 * A public pin. Country hosts, a slug (`/pin/<words>--<id>/`) and an invite's `/sent/` are the same pin, read only as
 * `https://www.pinterest.com/pin/<id>/`. `pin.it` is a short link, not a pin.
 */
internal class PinterestPostIdentity private constructor(val pinId: String) {
    val fetchUrl: String get() = "https://www.pinterest.com/pin/$pinId/"
    val widgetUrl: String get() = "https://widgets.pinterest.com/v3/pidgets/pins/info/?pin_ids=$pinId"

    companion object {
        private val host = Regex("(?:(?:www|[a-z]{2})\\.)?pinterest\\.(?:com|fr|de|ch|jp|cl|ca|it|co\\.uk|nz|ru|" +
            "com\\.au|at|pt|co\\.kr|es|com\\.mx|dk|ph|th|com\\.uy|co|nl|info|kr|ie|vn|com\\.vn|ec|mx|in|pe|co\\.at|" +
            "hu|co\\.in|co\\.nz|id|com\\.ec|com\\.py|tw|be|uk|com\\.bo|com\\.pe)")
        private val path = Regex("/pin/(?:[A-Za-z0-9_-]{1,200}--)?([0-9]{1,30})(?:/sent)?/?")
        private val login = Regex("/login(?:/.*)?")

        fun parse(raw: String): PinterestPostIdentity? = HttpsUrl.parse(raw)?.let(::parse)

        fun parse(url: HttpUrl): PinterestPostIdentity? {
            if (!host.matches(url.host)) return null
            return path.matchEntire(url.encodedPath)?.let { PinterestPostIdentity(it.groupValues[1]) }
        }

        fun isLoginWall(url: HttpUrl): Boolean = host.matches(url.host) && login.matches(url.encodedPath)
    }
}

/**
 * Pinterest, read from a pin's logged-out page - or, where it asks to sign in, the public widget JSON - for its video's
 * progressive MP4s of `video_list`, an idea pin's from its one video block. The pin is taken only from its own Redux entry,
 * `PinResource` or Relay result, never a related pin's. An image pin is not read. A `pin.it` link is followed first.
 */
internal object PinterestReader : SiteReader {
    override fun accepts(url: String): Boolean =
        PinterestPostIdentity.parse(url) != null || HttpsUrl.parse(url)?.let { it.host in ShortHosts && ShortLinks.isShortLink(it) } == true

    override fun read(url: String, http: SiteHttp): String? {
        val short = HttpsUrl.parse(url)?.takeIf { it.host in ShortHosts }
        val target = if (short != null) http.landing(url, ShortLinks::isShortLink)?.toString() ?: return null else url
        val identity = PinterestPostIdentity.parse(target) ?: return null
        // A redirect may drop its query on the same pin; a sign-in wall is not followed.
        val follow = { _: HttpUrl, to: HttpUrl ->
            to.takeIf { !PinterestPostIdentity.isLoginWall(it) && it.host == "www.pinterest.com" && PinterestPostIdentity.parse(it)?.pinId == identity.pinId }
                ?.newBuilder()?.query(null)?.build()
        }
        val page = http.get(identity.fetchUrl, MaxBytes, SiteHttp.MobilePage, follow)
        if (page != null) return parsePage(page.text, identity.pinId, identity.fetchUrl)
        val widget = http.get(identity.widgetUrl, MaxBytes, SiteHttp.Json) ?: return null
        return parseWidget(widget.text, identity.pinId, identity.fetchUrl)
    }

    /** The video of the pin [pinId] on its page - null for an image pin, or copies of it that disagree. */
    fun parsePage(html: String, pinId: String, webpageUrl: String): String? = runCatching {
        val found = ArrayList<JSONObject>()
        script(html, "__PWS_INITIAL_PROPS__")?.let { body ->
            JSONObject(body).optJSONObject("initialReduxState")?.optJSONObject("pins")?.optJSONObject(pinId)?.let(found::add)
        }
        script(html, "resource-response-data")?.let { body ->
            val resource = JSONObject(body)
            if (resource.optJSONObject("resource")?.opt("name") == "PinResource") {
                resource.optJSONObject("resource_response")?.optJSONObject("data")?.takeIf { it.opt("id")?.toString() == pinId }?.let(found::add)
            }
        }
        found += relayPins(html, pinId)
        val videos = found.map(::formatsOf)
        // Every copy of the pin must agree that it is a video.
        if (videos.isEmpty() || videos.any { it.isEmpty() }) return null
        infoJson(found.first(), videos.first(), pinId, webpageUrl)
    }.getOrNull()

    /** The video of the pin [pinId] in the widget's JSON, whose first result must be that pin. */
    fun parseWidget(json: String, pinId: String, webpageUrl: String): String? = runCatching {
        val root = JSONObject(json)
        if (root.opt("status") != "success") return null
        val pin = root.optJSONArray("data")?.optJSONObject(0)?.takeIf { it.opt("id")?.toString() == pinId } ?: return null
        infoJson(pin, formatsOf(pin).ifEmpty { return null }, pinId, webpageUrl)
    }.getOrNull()

    private fun infoJson(pin: JSONObject, formats: List<VideoFormat>, pinId: String, webpageUrl: String): String? {
        val list = videoList(pin)
        val durationMs = list?.keyList()?.firstNotNullOfOrNull { list.optJSONObject(it)?.opt("duration").wholeNumber() }
        val thumbnail = list?.keyList()?.firstNotNullOfOrNull { list.optJSONObject(it)?.text("thumbnail") }?.takeIf { HttpsUrl.parse(it) != null }
        val attribution = pin.optJSONObject("closeup_attribution") ?: pin.optJSONObject("closeupAttribution")
        val pinner = pin.optJSONObject("pinner")
        return videoInfoJson(
            VideoPost(
                id = pinId,
                title = listOf("grid_title", "gridTitle", "seo_title", "seoTitle", "title", "closeup_unified_description", "description")
                    .firstNotNullOfOrNull { firstLine(pin.text(it)) },
                uploader = listOfNotNull(attribution, pinner).firstNotNullOfOrNull { it.text("full_name") ?: it.text("fullName") },
                thumbnail = thumbnail,
                durationSeconds = durationMs?.div(1000.0),
                webpageUrl = webpageUrl,
                extractorKey = "Pinterest",
                formats = formats,
            ),
        )
    }

    /** Each MP4 of [pin]'s video list on Pinterest's video host, a format named by its quality. */
    private fun formatsOf(pin: JSONObject): List<VideoFormat> {
        val list = videoList(pin) ?: return emptyList()
        return list.keyList().sorted().mapNotNull { name ->
            val entry = list.optJSONObject(name) ?: return@mapNotNull null
            val url = entry.text("url")?.takeIf(::isVideo) ?: return@mapNotNull null
            VideoFormat(
                id = name.replace(Regex("[^A-Za-z0-9_.-]"), "_"),
                url = url,
                width = entry.opt("width").wholeNumber()?.toInt(),
                height = entry.opt("height").wholeNumber()?.toInt(),
                videoCodec = "h264",
                audioCodec = "aac",
            )
        }
    }

    /** [pin]'s `videos.video_list`, or an idea pin's one video block's. */
    private fun videoList(pin: JSONObject): JSONObject? {
        pin.optJSONObject("videos")?.let { it.optJSONObject("video_list") ?: it.optJSONObject("videoList") }?.let { return it }
        val story = pin.optJSONObject("story_pin_data") ?: pin.optJSONObject("storyPinData") ?: return null
        val blocks = story.optJSONArray("pages")?.objects().orEmpty().flatMap { it.optJSONArray("blocks")?.objects().orEmpty() }
        val videos = blocks.mapNotNull { block -> block.optJSONObject("video")?.let { it.optJSONObject("video_list") ?: it.optJSONObject("videoList") } }
        return videos.singleOrNull()
    }

    /** Relay results whose query names [pinId]; one that names it but holds another pin, or none, is refused. */
    private fun relayPins(page: String, pinId: String): List<JSONObject> {
        val found = ArrayList<JSONObject>()
        var from = 0
        repeat(MaxRelays) {
            val at = page.indexOf(Relay, from)
            if (at < 0) return found
            val open = page.indexOf('(', at)
            val quote = page.indexOf('"', open)
            val endQuote = page.indexOf('"', quote + 1)
            if (open < 0 || quote < 0 || endQuote < 0 || quote - open > 4 || endQuote - quote > 8 * 1024) {
                from = at + Relay.length
                return@repeat
            }
            val named = runCatching { URLDecoder.decode(page.substring(quote + 1, endQuote), "UTF-8") }.getOrNull()
                ?.contains("\"pinId\":\"$pinId\"") == true
            val brace = page.indexOf('{', endQuote)
            if (brace < 0 || brace - endQuote > 9) {
                if (named) error("refused")
                from = endQuote + 1
                return@repeat
            }
            val tokener = JSONTokener(page.substring(brace))
            val value = runCatching { tokener.nextValue() as? JSONObject }.getOrNull()
            from = brace + 1
            if (!named) return@repeat
            val pin = value?.optJSONObject("data")?.optJSONObject("v3GetPinQueryv2")?.optJSONObject("data") ?: error("refused")
            if (!namesPin(pin, pinId)) error("refused")
            found += pin
        }
        return found
    }

    /** `entityId`, or for a light result without one, its Relay node id `base64("Pin:<id>")`. */
    private fun namesPin(pin: JSONObject, pinId: String): Boolean {
        pin.opt("entityId")?.let { return it.toString() == pinId }
        val node = pin.text("id")?.takeIf { it.length <= 64 } ?: return false
        return runCatching { String(Base64.getDecoder().decode(node), Charsets.UTF_8) }.getOrNull() == "Pin:$pinId"
    }

    private fun script(page: String, id: String): String? {
        val at = page.indexOf("id=\"$id\"")
        if (at < 0) return null
        val tagEnd = page.indexOf('>', at)
        val close = page.indexOf("</script>", at)
        if (tagEnd < 0 || close < 0 || tagEnd > close) return null
        return page.substring(tagEnd + 1, close)
    }

    private fun isVideo(raw: String): Boolean {
        val url = HttpsUrl.parse(raw) ?: return false
        return Regex("v[0-9]*\\.pinimg\\.com").matches(url.host) && !url.encodedPath.contains("..") && url.encodedPath.endsWith(".mp4")
    }

    private val ShortHosts = setOf("pin.it", "api.pinterest.com")
    private const val Relay = "window.__PWS_RELAY_REGISTER_COMPLETED_REQUEST__("
    private const val MaxRelays = 8
}
