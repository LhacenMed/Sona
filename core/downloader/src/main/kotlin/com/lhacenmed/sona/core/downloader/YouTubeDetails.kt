package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.downloader.sites.compactCountOf
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * A YouTube video's details - when it was published, how often watched, liked and commented on - read from its watch page's
 * own data, `ytInitialData`: what the page shows anyone, signed in or not. Not its player's data, which YouTube can refuse a
 * reader it takes for a bot ("Sign in to confirm you're not a bot") - nor the player's code, which a read of its streams has
 * to fetch and work through first. One request, so the details can be read again whenever they are asked for.
 *
 * Asked for in American English, so its words and numbers are read alike everywhere - see [statsJsonOf].
 */
internal object YouTubeDetails {

    /** The canonical watch page of [url]'s video - null for a link that is not one YouTube video's. */
    fun watchUrlOf(url: String): String? = linkKeyOf(url).takeIf { it.startsWith(WatchPrefix) }

    /** [watchUrl]'s details as yt-dlp's fields - see [VideoStatsFields]. Throws where the page cannot be read, or holds none. */
    fun read(watchUrl: String, client: OkHttpClient): String {
        val request = Request.Builder()
            .url("$watchUrl&hl=en&gl=US&has_verified=1")
            .header("User-Agent", BrowserUserAgent)
            .header("Accept-Language", "en-US,en;q=0.9")
            // Past the consent page a European visitor is shown first.
            .header("Cookie", "SOCS=CAI; CONSENT=YES+")
            .build()
        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube answered ${response.code}")
            response.body?.string().orEmpty()
        }
        val data = initialDataOf(html) ?: throw IOException("YouTube's page held none of the video's data")
        return statsJsonOf(data, watchUrl) ?: throw IOException("YouTube's page held none of the video's details")
    }

    /** The page's `ytInitialData` - null where it has none. */
    internal fun initialDataOf(html: String): JSONObject? {
        val start = InitialData.find(html)?.range?.last ?: return null
        return runCatching { JSONTokener(html.substring(start + 1)).nextValue() as? JSONObject }.getOrNull()
    }

    /** [data]'s details, kept with [watchUrl] - null where it holds none of them. */
    internal fun statsJsonOf(data: JSONObject, watchUrl: String): String? {
        val views = first(data, "videoViewCountRenderer")?.let { renderer ->
            renderer.optString("originalViewCount").toLongOrNull()
                ?: renderer.optJSONObject("viewCount")?.optString("simpleText")?.filter(Char::isDigit)?.toLongOrNull()
        }
        val day = (first(data, "videoPrimaryInfoRenderer")?.optJSONObject("dateText")?.optString("simpleText")
            ?: first(data, "videoDescriptionHeaderRenderer")?.optJSONObject("publishDate")?.optString("simpleText"))
            ?.let(::dayOf)
        val likes = YouTubeExtras.likesOf(data)
        val comments = commentsOf(data)
        if (views == null && day == null && likes == null && comments == null) return null
        return JSONObject()
            .put("upload_date", day)
            .put("view_count", views)
            .put("like_count", likes)
            .put("comment_count", comments)
            .put("webpage_url", watchUrl)
            .toString()
    }

    /** "Oct 23, 2026", "Premiered Oct 23, 2026", "Streamed live on Oct 23, 2026" as yt-dlp's day, "20261023" - null for none. */
    internal fun dayOf(text: String): String? {
        val match = DayWords.find(text) ?: return null
        return runCatching {
            LocalDate.parse(match.value, DayFormat).format(DateTimeFormatter.BASIC_ISO_DATE)
        }.getOrNull()
    }

    /** How often it was commented on, as its comments panel's header shows it - "1.9K", rounded where it is large. */
    private fun commentsOf(data: JSONObject): Long? {
        val panels = data.optJSONArray("engagementPanels") ?: return null
        return (0 until panels.length()).firstNotNullOfOrNull { index ->
            val panel = panels.optJSONObject(index)?.optJSONObject("engagementPanelSectionListRenderer") ?: return@firstNotNullOfOrNull null
            if (panel.optString("panelIdentifier") != "engagement-panel-comments-section") return@firstNotNullOfOrNull null
            panel.optJSONObject("header")?.optJSONObject("engagementPanelTitleHeaderRenderer")?.optJSONObject("contextualInfo")
                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.let(::compactCountOf)
        }
    }

    /** The first object under [key] within [value], depth first. */
    private fun first(value: Any?, key: String, depth: Int = 0): JSONObject? = when {
        depth > MaxDepth -> null
        value is JSONObject -> value.optJSONObject(key)
            ?: value.keys().asSequence().firstNotNullOfOrNull { first(value.opt(it), key, depth + 1) }
        value is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { first(value.opt(it), key, depth + 1) }
        else -> null
    }

    private const val WatchPrefix = "https://www.youtube.com/watch?v="
    private const val MaxDepth = 64
    private const val BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    private val InitialData = Regex("""ytInitialData\s*=\s*(?=\{)""")
    private val DayWords = Regex("""[A-Z][a-z]{2} \d{1,2}, \d{4}""")
    private val DayFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
}
