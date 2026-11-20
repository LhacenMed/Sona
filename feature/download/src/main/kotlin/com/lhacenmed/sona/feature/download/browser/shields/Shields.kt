/*
 * Brave Shields, as brave-core decides them - components/brave_shields' ad block engine wrapper, HTTPS upgrades, cookie
 * controls, farbling and "forget me" - Copyright The Brave Authors, licensed under the Mozilla Public License, Version 2.0:
 * https://mozilla.org/MPL/2.0/ - for the browser's web views.
 */
package com.lhacenmed.sona.feature.download.browser.shields

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import java.io.ByteArrayInputStream
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.model.AdBlockMode
import com.lhacenmed.sona.core.model.CookieBlockMode
import com.lhacenmed.sona.core.model.FingerprintMode
import com.lhacenmed.sona.core.model.HttpsUpgradeMode
import com.lhacenmed.sona.core.model.ShieldsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Shields of every page the browser opens: what of a page's requests is blocked, where a link goes cleaned of its
 * trackers, what is asked over HTTPS, and what the script a page starts with does - each as [ShieldsPreferences] says, but
 * on a site whose Shields are down.
 */
@Singleton
class Shields @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val settings: DownloadSettings,
    val lists: ShieldsLists,
) {
    val preferences: ShieldsPreferences get() = settings.browserShields.value

    /** A key of this run of the app's, from which each site's farbling is seeded - a new one each run, as Brave's session's. */
    private val sessionKey = ByteArray(32).also(SecureRandom()::nextBytes)

    /** Sites that failed over HTTPS this run - see [fallBackFrom] - asked for over HTTP from then on. */
    private val insecureHosts = ConcurrentHashMap.newKeySet<String>()

    /** What the page script reaches Kotlin by - a name of this run's own, so no page can count on finding it. */
    internal val bridgeName = "_" + List(12) { "abcdefghijklmnopqrstuvwxyz"[SecureRandom().nextInt(26)] }.joinToString("")

    /**
     * The script every frame starts with, before its own: uBO's and Brave's scriptlets, Brave's procedural filters, and the
     * Shields' own - see assets/browser - in one scope of their own, out of the page's.
     */
    internal val pageScript: String by lazy {
        fun asset(name: String) = context.assets.open("browser/$name").bufferedReader().use { it.readText() }
        listOf(asset("scriptlets.js"), asset("procedural.js"), asset("shields.js").replace("__BRIDGE__", bridgeName))
            .joinToString(separator = "\n", prefix = "(function () {\n", postfix = "\n})();")
    }

    /** uBO's stand-ins by every name a filter calls one - see assets/browser/redirects. */
    private val redirects: Map<String, String> by lazy {
        val json = JSONObject(context.assets.open("browser/redirects.json").bufferedReader().use { it.readText() })
        json.keys().asSequence().associateWith(json::getString)
    }

    /** What is served in a blocked request's place: uBO's stand-in a filter names - or nothing, an empty answer. */
    internal fun blockedResponse(request: FilterRequest): WebResourceResponse {
        val loaded = lists.loaded.value
        val name = loaded?.defaultEngine?.redirectFor(request) ?: loaded?.additionalEngine?.redirectFor(request)
        val file = name?.let(redirects::get)
        if (file != null) {
            val stream = runCatching { context.assets.open("browser/redirects/$file") }.getOrNull()
            if (stream != null) return WebResourceResponse(mimeOf(file), "utf-8", stream)
        }
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    /** Whether a window a page on [pageHost] opens with no tap asking for it is blocked. */
    internal fun blocksUnaskedPopups(pageHost: String?): Boolean = preferences.adBlock != AdBlockMode.OFF && isUp(pageHost)

    /** Whether a window [opener]'s page opened at [url] is a popup the lists block - by its `$popup` filters. */
    internal fun blocksPopup(url: Uri, opener: String?): Boolean {
        val host = url.host?.lowercase() ?: return false
        return shouldBlock(FilterRequest(url.toString(), host, opener?.lowercase() ?: host, RequestType.POPUP))
    }

    fun isUp(host: String?, shields: ShieldsPreferences = preferences): Boolean = host == null || siteOf(host.lowercase()) !in shields.downSites

    /** Takes [host]'s site's Shields up, or down. */
    fun setUp(host: String, up: Boolean) {
        val site = siteOf(host.lowercase())
        scope.launch { settings.updateShields { it.copy(downSites = if (up) it.downSites - site else it.downSites + site) } }
    }

    fun update(change: (ShieldsPreferences) -> ShieldsPreferences) {
        scope.launch { settings.updateShields(change) }
    }

    /**
     * Whether [request] is blocked - as Brave's engine wrapper decides it: its default lists' filters count against a page's
     * own requests only where blocking is aggressive, its other lists' always, and an exception lets through what is not
     * `$important`.
     */
    internal fun shouldBlock(request: FilterRequest): Boolean {
        val shields = preferences
        if (shields.adBlock == AdBlockMode.OFF || !isUp(request.pageHost)) return false
        val loaded = lists.loaded.value ?: return false
        val countsDefault = shields.adBlock == AdBlockMode.AGGRESSIVE || request.isThirdParty
        val default = if (countsDefault) loaded.defaultEngine?.blocking(request) ?: Block.NONE else Block.NONE
        if (default == Block.IMPORTANT) return true
        val additional = loaded.additionalEngine?.blocking(request) ?: Block.NONE
        if (additional == Block.IMPORTANT) return true
        if (default == Block.NONE && additional == Block.NONE) return false
        return loaded.defaultEngine?.excepts(request) != true && loaded.additionalEngine?.excepts(request) != true
    }

    /** Where a link to [url] from [fromHost] goes, past the trackers it passes through and those it carries - null where it is as it was. */
    internal fun cleaned(url: Uri, fromHost: String?): Uri? {
        val shields = preferences
        if (!isUp(fromHost) || !isUp(url.host)) return null
        val loaded = lists.loaded.value ?: return null
        val debounced = if (shields.debounce || shields.deAmp) debounce(url, loaded.debounceRules, loaded.debounceSites, shields.deAmp) else null
        val target = debounced ?: url
        return filterQuery(target, fromHost, loaded.queryRules) ?: debounced
    }

    /** [url] over HTTPS where it is asked over HTTP and may be - null where it is not upgraded. */
    internal fun upgraded(url: Uri): Uri? {
        val host = url.host?.lowercase() ?: return null
        if (url.scheme != "http" || preferences.httpsUpgrade == HttpsUpgradeMode.OFF || !isUp(host)) return null
        if (isLocalHost(host) || host in insecureHosts) return null
        return url.buildUpon().scheme("https").build()
    }

    /**
     * [url], upgraded, failed over HTTPS: its HTTP page, from now on, where the upgrade is standard - null where it is strict, and
     * the user is asked first.
     */
    internal fun fallBackFrom(url: Uri): Uri? {
        if (preferences.httpsUpgrade == HttpsUpgradeMode.STRICT) return null
        return insecure(url)
    }

    /** [url]'s HTTP page, which its site is asked over from now on - the user's own choice past the strict warning. */
    internal fun insecure(url: Uri): Uri? {
        val host = url.host?.lowercase() ?: return null
        insecureHosts += host
        return url.buildUpon().scheme("http").build()
    }

    /** Whether a page on [host] may run scripts. */
    internal fun allowsScripts(host: String?): Boolean = !preferences.blockScripts || !isUp(host)

    /** Whether a page on [host] may have cookies at all - and other sites' in it. */
    internal fun allowsCookies(host: String?): Boolean = preferences.cookies != CookieBlockMode.ALL || !isUp(host)

    internal fun allowsCrossSiteCookies(host: String?): Boolean =
        preferences.cookies == CookieBlockMode.OFF || !isUp(host)

    /**
     * What a site leaves on the device - its cookies and its storage - gone, for each of [hosts] whose Shields forget it as its
     * last tab closes: Brave's "Forget me when I close this site".
     */
    internal fun forget(hosts: Collection<String>) {
        if (!preferences.forgetOnClose) return
        val cookies = CookieManager.getInstance()
        hosts.filter(::isUp).forEach { host ->
            val site = siteOf(host)
            listOf("https://$host", "http://$host").forEach { origin ->
                cookies.getCookie(origin)?.split(';')?.map { it.substringBefore('=').trim() }?.filter(String::isNotEmpty)?.forEach { name ->
                    cookies.setCookie(origin, "$name=; Max-Age=0; Path=/")
                    cookies.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=$site")
                }
                WebStorage.getInstance().deleteOrigin(origin)
            }
        }
        cookies.flush()
    }

    /** The farbling seed of [site] for this run: the session key's HMAC of it, as Brave seeds its own. */
    private fun seedOf(site: String): Long {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(sessionKey, "HmacSHA256")) }
        val bytes = mac.doFinal(site.toByteArray())
        return (0 until 4).fold(0L) { seed, index -> seed shl 8 or (bytes[index].toLong() and 0xFF) }
    }

    /** What the page script is called on - from every frame, on a thread of the web view's own. */
    internal inner class Bridge {
        /** What a frame's script does - for [frameUrl], on [frameHost], under the top page on [topHost] - as JSON. */
        @JavascriptInterface
        fun config(topHost: String, frameHost: String, frameUrl: String): String {
            val shields = preferences
            val top = topHost.lowercase()
            if (!isUp(top)) return """{"up":false}"""
            val aggressive = shields.adBlock == AdBlockMode.AGGRESSIVE
            val loaded = lists.loaded.value.takeIf { shields.adBlock != AdBlockMode.OFF }
            val host = frameHost.lowercase()
            // The default lists' `:has` selectors hide only where blocking is aggressive - the others' always, as Brave's.
            val defaultHiding = loaded?.defaultEngine?.pageHiding(frameUrl, host, aggressive)
            val additionalHiding = loaded?.additionalEngine?.pageHiding(frameUrl, host, aggressive = true)
            val hide = (defaultHiding?.selectors.orEmpty() + additionalHiding?.selectors.orEmpty()).distinct()
            // Scriptlets: each its trust - only Brave's default lists' may run uBO's trusted ones - its name, its arguments.
            val scriptlets = JSONArray()
            loaded?.defaultEngine?.scriptletsFor(host)?.forEach { scriptlets.put(JSONArray(listOf(true) + it)) }
            loaded?.additionalEngine?.scriptletsFor(host)?.forEach { scriptlets.put(JSONArray(listOf(false) + it)) }
            // Procedural filters - the default lists' only where blocking is aggressive, as Brave strips them otherwise.
            val procedural = JSONArray()
            (loaded?.defaultEngine?.proceduralFor(host, aggressive).orEmpty() + loaded?.additionalEngine?.proceduralFor(host, aggressive = true).orEmpty())
                .distinct().forEach { procedural.put(JSONObject(it)) }
            return JSONObject()
                .put("up", true)
                .put("fp", when (shields.fingerprinting) { FingerprintMode.OFF -> 0; FingerprintMode.STANDARD -> 1; FingerprintMode.STRICT -> 2 })
                .put("seed", seedOf(siteOf(top)))
                .put("lang", shields.reduceLanguage && shields.fingerprinting != FingerprintMode.OFF)
                .put("amp", shields.deAmp)
                .put("hide", JSONArray(hide))
                .put("scriptlets", scriptlets)
                .put("procedural", procedural)
                .put("byClassAndId", defaultHiding?.byClassAndId == true || additionalHiding?.byClassAndId == true)
                .toString()
        }

        /** The generic selectors hiding an element of one of [classes] or [ids] - JSON arrays - on [frameHost]: a JSON array. */
        @JavascriptInterface
        fun classIdSelectors(frameHost: String, classes: String, ids: String): String {
            val shields = preferences
            val loaded = lists.loaded.value?.takeIf { shields.adBlock != AdBlockMode.OFF } ?: return "[]"
            val host = frameHost.lowercase()
            val classList = runCatching { JSONArray(classes).let { array -> List(array.length()) { array.optString(it) } } }.getOrDefault(emptyList())
            val idList = runCatching { JSONArray(ids).let { array -> List(array.length()) { array.optString(it) } } }.getOrDefault(emptyList())
            val aggressive = shields.adBlock == AdBlockMode.AGGRESSIVE
            val found = loaded.defaultEngine?.classIdSelectors(host, classList, idList, aggressive).orEmpty() +
                loaded.additionalEngine?.classIdSelectors(host, classList, idList, aggressive = true).orEmpty()
            return JSONArray(found.distinct()).toString()
        }
    }
}

/** What a request [url] asks for, as its `Accept` header and its file's extension tell - null where neither does. */
internal fun requestTypeOf(url: Uri, headers: Map<String, String>, isMainFrame: Boolean): RequestType? {
    if (isMainFrame) return RequestType.DOCUMENT
    val accept = headers.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value.orEmpty()
    val requestedWith = headers.entries.firstOrNull { it.key.equals("X-Requested-With", ignoreCase = true) }?.value
    when {
        accept.startsWith("text/html") -> return RequestType.SUBDOCUMENT
        accept.startsWith("text/css") -> return RequestType.STYLESHEET
        accept.startsWith("image/") -> return RequestType.IMAGE
        "video/" in accept || "audio/" in accept -> return RequestType.MEDIA
        requestedWith == "XMLHttpRequest" -> return RequestType.XHR
    }
    return when (url.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
        "js", "mjs" -> RequestType.SCRIPT
        "css" -> RequestType.STYLESHEET
        "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif", "bmp" -> RequestType.IMAGE
        "woff", "woff2", "ttf", "otf", "eot" -> RequestType.FONT
        "mp4", "webm", "m3u8", "mpd", "ts", "m4s", "mp3", "m4a", "aac", "ogg", "opus", "wav" -> RequestType.MEDIA
        else -> null
    }
}

/** The type a stand-in is served as, by its file's extension. */
private fun mimeOf(file: String): String = when (file.substringAfterLast('.', "")) {
    "js" -> "application/javascript"
    "css" -> "text/css"
    "json" -> "application/json"
    "gif" -> "image/gif"
    "png" -> "image/png"
    "mp3" -> "audio/mpeg"
    "mp4" -> "video/mp4"
    "xml" -> "text/xml"
    "html" -> "text/html"
    else -> "text/plain"
}
