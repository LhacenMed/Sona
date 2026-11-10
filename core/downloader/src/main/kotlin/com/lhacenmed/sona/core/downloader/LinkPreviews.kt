package com.lhacenmed.sona.core.downloader

import android.content.Context
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** What a link is shown as while it is still being read - its picture, its title, who published it - never what downloads it. */
data class LinkPreview(val title: String?, val uploader: String?, val thumbnailUrl: String?)

/** How long a preview is waited for: past it, the full read is what shows the link. */
private const val PreviewBudgetMs = 4_000L

/** The most an oEmbed answer is let be: its JSON is a few hundred bytes. */
private const val MaxPreviewBytes = 32L * 1024

/** How many links' previews are kept on the device, the most recently shown first. */
private const val KeptPreviews = 300

private val VideoId = Regex("[A-Za-z0-9_-]{11}")

/** A site that says what a link of its shows, in one small answer - its oEmbed, at the address [endpointFor] makes. */
private class OEmbedSite(val hosts: Set<String>, val endpointFor: (HttpUrl) -> HttpUrl?)

private fun oEmbed(host: String, path: String, url: String, vararg query: Pair<String, String>): HttpUrl =
    HttpUrl.Builder().scheme("https").host(host).encodedPath(path).addQueryParameter("url", url)
        .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
        .build()

private val YouTube = OEmbedSite(setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be")) { link ->
    youTubeVideoIdOf(link)?.let { oEmbed("www.youtube.com", "/oembed", "https://www.youtube.com/watch?v=$it", "format" to "json") }
}

/** The sites shown before they are read, YouTube first - adding one is adding it here. */
private val OEmbedSites = listOf(
    YouTube,
    OEmbedSite(setOf("vimeo.com", "www.vimeo.com", "player.vimeo.com")) { oEmbed("vimeo.com", "/api/oembed.json", it.toString()) },
    OEmbedSite(setOf("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com", "on.soundcloud.com")) {
        oEmbed("soundcloud.com", "/oembed", it.toString(), "format" to "json")
    },
    OEmbedSite(setOf("tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com")) { oEmbed("www.tiktok.com", "/oembed", it.toString()) },
)

/**
 * Links shown the moment they are given - Nezzel's early preview, for every site that offers one: a YouTube video's picture
 * from its id, with no request at all, then the title and the channel from the site's oEmbed, one small answer - both while
 * [MediaResolver] reads the link in full.
 *
 * Every link shown is kept on the device, so one opened before - read by oEmbed or in full, from any site - shows at once,
 * even after the app restarts; the picture is kept by the image loader's own disk cache.
 */
@Singleton
class LinkPreviews @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .callTimeout(PreviewBudgetMs, TimeUnit.MILLISECONDS)
        .build()

    private val file = File(context.noBackupFilesDir, "link-previews.json")

    /** The previews kept, by link, the least recently shown first - read from [file] once, as first asked for. */
    private val kept by lazy {
        LinkedHashMap<String, LinkPreview>(KeptPreviews, 0.75f, true).apply {
            runCatching { JSONArray(file.readText()) }.getOrNull()?.let { entries ->
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    put(entry.getString("url"), LinkPreview(entry.stringOrNull("title"), entry.stringOrNull("uploader"), entry.stringOrNull("thumbnail")))
                }
            }
        }
    }

    /**
     * What [url] is known to show before anything is asked: what it showed when last opened, or a YouTube video's picture -
     * null for a link never seen before on a site that shows nothing without a request.
     */
    suspend fun known(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        synchronized(this@LinkPreviews) { kept[url] } ?: url.toHttpUrlOrNull()?.let(::youTubeVideoIdOf)?.let { LinkPreview(null, null, youTubeThumbnailOf(it)) }
    }

    /** [url]'s title, channel and picture, as its site's oEmbed gives them - null for a site with none, or where it does not answer. */
    suspend fun read(url: String): LinkPreview? {
        val link = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        val endpoint = OEmbedSites.firstOrNull { link.host in it.hosts }?.endpointFor?.invoke(link) ?: return null
        val preview = withTimeoutOrNull(PreviewBudgetMs) {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.newCall(Request.Builder().url(endpoint).build()).execute().use { response ->
                        val source = response.body?.takeIf { response.isSuccessful }?.source() ?: return@use null
                        source.request(MaxPreviewBytes + 1)
                        if (source.buffer.size > MaxPreviewBytes) return@use null
                        val json = JSONObject(source.buffer.readUtf8())
                        LinkPreview(
                            title = json.stringOrNull("title")?.take(300),
                            uploader = json.stringOrNull("author_name")?.take(120),
                            thumbnailUrl = json.stringOrNull("thumbnail_url")?.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
                                ?: youTubeVideoIdOf(link)?.let(::youTubeThumbnailOf),
                        ).takeIf { it.title != null }
                    }
                }.getOrNull()
            }
        }
        preview?.let { remember(url, it) }
        return preview
    }

    /** Keeps what [info], [url] read in full, shows - for the next time it is opened. */
    fun remember(url: String, info: MediaInfo) = remember(url, LinkPreview(info.title, info.uploader, info.thumbnailUrl))

    private fun remember(url: String, preview: LinkPreview) {
        synchronized(this) {
            kept.remove(url)
            kept[url] = preview
            while (kept.size > KeptPreviews) kept.remove(kept.keys.first())
        }
        scope.launch(Dispatchers.IO) { save() }
    }

    private fun save() {
        val entries = synchronized(this) {
            JSONArray(kept.map { (url, preview) ->
                JSONObject().put("url", url).put("title", preview.title).put("uploader", preview.uploader).put("thumbnail", preview.thumbnailUrl)
            })
        }
        // Written whole, then put in place: a save cut short never leaves half a file to read back.
        val partial = File(file.path + ".part")
        runCatching {
            partial.writeText(entries.toString())
            partial.renameTo(file)
        }
    }
}

/** The image oEmbed names for YouTube video [id], known before any request, so it loads while oEmbed runs. */
private fun youTubeThumbnailOf(id: String) = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

/** The id of the one YouTube video [link] leads to - a watch page, a short, an embed, a live, a youtu.be link - or null. */
private fun youTubeVideoIdOf(link: HttpUrl): String? {
    if (!link.isHttps || link.host !in YouTube.hosts) return null
    val id = when {
        link.host.endsWith("youtu.be") -> link.pathSegments.singleOrNull()
        link.encodedPath == "/watch" -> link.queryParameterValues("v").singleOrNull()
        link.pathSegments.size == 2 && link.pathSegments[0] in setOf("shorts", "embed", "live") -> link.pathSegments[1]
        else -> null
    }
    return id?.takeIf(VideoId::matches)
}
