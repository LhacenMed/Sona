/*
 * Adapted from AB Download Manager's Android browser - BrowserComponent, WebViewHolder and DownloadInterceptor -
 * Copyright AB Download Manager contributors, licensed under the Apache License, Version 2.0:
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.lhacenmed.sona.feature.download.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.lhacenmed.sona.core.downloader.BrowserCookies
import com.lhacenmed.sona.core.model.SearchEngine
import android.text.TextUtils
import org.json.JSONObject
import org.json.JSONTokener
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.browser.shields.FilterRequest
import com.lhacenmed.sona.feature.download.browser.shields.Shields
import com.lhacenmed.sona.feature.download.browser.shields.requestTypeOf

/** The scheme of the Shields' own pages' links - the HTTPS warning's. */
private const val ShieldsScheme = "sona-shields:"

/** Where a new tab opens with no page asked for. */
private const val BlankPage = "about:blank"

/** How long a page's request is remembered, for its headers to go with a download it turns out to be. */
private const val RememberedRequestMs = 20_000L

/** Headers a page's request carries that are its own to that one request, never a download's. */
private val RequestOnlyHeaders = setOf("range", "if-range", "if-none-match", "if-modified-since")

/** A site's address typed with no scheme before it - `example.com`, `example.co.uk:8080`, `m.youtube.com/watch?v=...`. */
private val WebsiteAndTld = Regex("""^[\w.-]+\.[a-zA-Z]{2,}(:\d{1,5})?([/?#]\S*)?$""")

/** How small, and how large, a page's text is drawn, as a percent of its own - and the step between. */
internal const val MinTextZoom = 50
internal const val MaxTextZoom = 300
internal const val TextZoomStep = 10

/**
 * One tab: its own web view - made as it is first shown, kept while the tab is open, paused while another is in front - and
 * what it shows, as the bar reads it.
 */
@Stable
class BrowserTab internal constructor(
    val id: String,
    internal val firstPage: String?,
    internal val openedBy: String?,
    private val shields: Shields,
) {
    var url: String? by mutableStateOf(firstPage?.takeUnless { it == BlankPage })
        internal set
    var title: String? by mutableStateOf(null)
        internal set
    var icon: Bitmap? by mutableStateOf(null)
        internal set
    /** How far the page has loaded, 0 to 1 - null once it has. */
    var progress: Float? by mutableStateOf(null)
        internal set
    var canGoBack: Boolean by mutableStateOf(false)
        internal set
    var canGoForward: Boolean by mutableStateOf(false)
        internal set

    internal var webView: WebView? = null

    /** Whether the page is asked for as a computer's browser asks - the whole site, not its phone's. */
    var isDesktopSite: Boolean by mutableStateOf(false)
        internal set

    /** How large the page's text is drawn, as a percent of its own. */
    var textZoom: Int by mutableIntStateOf(100)
        internal set

    /** What was found of what is looked for in the page: which match is shown, from 1, of how many - null while nothing is. */
    var foundMatches: Pair<Int, Int>? by mutableStateOf(null)
        internal set

    /** The page that opened this tab as a new window, where one did - a download it starts came from there. */
    internal var openerPage: String? = null

    /** What the Shields blocked of this page's requests, and how many of its loads they asked over HTTPS instead. */
    var blockedCount: Int by mutableIntStateOf(0)
        internal set
    var upgradedCount: Int by mutableIntStateOf(0)
        internal set

    /** The host of the page shown, or being gone to - read on the web view's own threads, as its requests are seen. */
    @Volatile
    internal var pageHost: String? = null

    /** The host last asked over HTTPS in HTTP's place - where its page fails, the upgrade did. */
    internal var upgradedHost: String? = null

    /** Whether this tab, opened by a page as a window, had its first page checked against the lists' popup filters. */
    internal var isPopupChecked = false

    /** Every host this tab's pages were on - what "forget me" forgets of, as it closes. */
    internal val visitedHosts = mutableSetOf<String>()

    /** Opens [url] - cleaned of trackers, over HTTPS where it may be - asking it not to sell or share what it learns. */
    fun load(url: String) {
        val webView = webView ?: return
        val asked = Uri.parse(url)
        val cleaned = shields.cleaned(asked, fromHost = null) ?: asked
        upgradedCount = 0
        val target = shields.upgraded(cleaned)?.also { upgradedHost = it.host; upgradedCount++ } ?: cleaned
        goingTo(webView, target.host)
        webView.loadUrl(target.toString(), if (shields.isUp(target.host)) mapOf("Sec-GPC" to "1") else emptyMap())
    }

    /** Readies [webView] for a page on [host]: its scripts, and its cookies - and other sites' - as its Shields allow. */
    internal fun goingTo(webView: WebView, host: String?) {
        pageHost = host?.lowercase()
        webView.settings.javaScriptEnabled = shields.allowsScripts(host)
        CookieManager.getInstance().setAcceptCookie(shields.allowsCookies(host))
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, shields.allowsCrossSiteCookies(host))
    }

    fun goBack() {
        webView?.goBack()
    }

    fun goForward() {
        webView?.goForward()
    }

    /** Home - the start page - in this tab's place; back is the page it left. */
    fun goHome() {
        webView?.loadUrl(BlankPage)
    }

    /** Loads the page again - as its Shields are now, where they changed. */
    fun reload() {
        val webView = webView ?: return
        goingTo(webView, url?.let(Uri::parse)?.host)
        webView.reload()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }

    /** Asks for the page again as a computer's browser - or a phone's - would. */
    fun setDesktopSite(desktop: Boolean) {
        val webView = webView ?: return
        isDesktopSite = desktop
        webView.settings.userAgentString = if (desktop) desktopUserAgentOf(webView.context) else null
        webView.settings.useWideViewPort = desktop
        webView.settings.loadWithOverviewMode = desktop
        webView.reload()
    }

    fun setTextZoom(percent: Int) {
        textZoom = percent.coerceIn(MinTextZoom, MaxTextZoom)
        webView?.settings?.textZoom = textZoom
    }

    /** Looks for [text] in the page, marking every match - see [foundMatches]. */
    fun find(text: String) {
        if (text.isEmpty()) clearFind() else webView?.findAllAsync(text)
    }

    fun findNext(forward: Boolean) {
        webView?.findNext(forward)
    }

    fun clearFind() {
        webView?.clearMatches()
        foundMatches = null
    }

    /** The page as Google translates it into [language]. */
    fun translate(language: String) {
        val page = url ?: return
        load("https://translate.google.com/translate?sl=auto&tl=$language&u=" + URLEncoder.encode(page, Charsets.UTF_8.name()))
    }

    /**
     * The page's article alone - its words and pictures, as [readability] finds them: Mozilla's Readability, Firefox's Reader
     * View - laid out to read in [style], in the page's place; back is the page again. [onNone] where it has no article.
     */
    internal fun showReadingMode(readability: String, style: ReaderStyle, onNone: () -> Unit) {
        val webView = webView ?: return
        val page = url ?: return
        val read = "$readability\n;(function(){var a=new Readability(document.cloneNode(true)).parse();" +
            "return a?JSON.stringify({title:a.title,content:a.content}):null})()"
        webView.evaluateJavascript(read) { result ->
            // The script's answer comes back as JSON of its own: a string holding the article's JSON.
            val article = runCatching { JSONObject(JSONTokener(result).nextValue() as String) }.getOrNull() ?: return@evaluateJavascript onNone()
            webView.loadDataWithBaseURL(page, style.page(article.optString("title"), article.optString("content")), "text/html", "utf-8", page)
        }
    }
}

/** The colours an article is read in - the app's own - each as CSS takes it: `#RRGGBB`. */
internal class ReaderStyle(private val background: String, private val text: String, private val link: String) {
    fun page(title: String, content: String): String =
        "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>" +
            "body{margin:0;padding:24px 20px 64px;background:$background;color:$text;font:18px/1.65 sans-serif;overflow-wrap:break-word}" +
            "h1{font-size:1.5em;line-height:1.3;margin:0 0 .8em}img,video,figure,iframe{max-width:100%;height:auto}" +
            "a{color:$link}pre{white-space:pre-wrap}</style></head><body><h1>${TextUtils.htmlEncode(title)}</h1>$content</body></html>"
}

/** Who a computer's browser says it is: [context]'s web view's own, its phone named as a computer, and "Mobile" gone. */
private fun desktopUserAgentOf(context: Context): String =
    WebSettings.getDefaultUserAgent(context).replace(Regex("""\(Linux; Android[^)]*\)"""), "(X11; Linux x86_64)").replace(" Mobile", "")

/** A link pressed and held in a page - what the link menu offers to do with it. */
data class HeldLink(val url: String, val tab: BrowserTab)

/** A download a page started, with what its request carried - see [BrowserTabs.onDownload]. */
data class CaughtDownload(val url: String, val headers: Map<String, String>)

/** A page's full-screen view - a video played full screen - and how to leave it. */
class FullScreenView(val view: View, val onExit: () -> Unit)

/**
 * Every open tab of the browser, the one in front, and the web views behind them: AB Download Manager's, tab for tab. A page that
 * opens a window opens a tab beside its own; a download any page starts is caught - with the headers its request carried, the
 * browser's cookies for it and who the browser says it is - and handed to [onDownload], never to the system; a tab opened for
 * nothing but a download closes as it is caught.
 */
@Stable
class BrowserTabs(
    private val onDownload: (CaughtDownload) -> Unit,
    private val onLinkHeld: (HeldLink) -> Unit,
    private val searchEngine: () -> SearchEngine,
    private val shields: Shields,
    private val cookies: BrowserCookies,
) {
    val tabs = mutableStateListOf<BrowserTab>()
    var activeIndex by mutableIntStateOf(-1)
        private set
    val activeTab: BrowserTab? get() = tabs.getOrNull(activeIndex)

    /** What a page shows full screen while it does - a video's player. */
    var fullScreen: FullScreenView? by mutableStateOf(null)
        private set

    /** The pages' requests of late, by address - see [RememberedRequestMs]. */
    private val requests = ConcurrentHashMap<String, Pair<Long, Map<String, String>>>()

    /** Opens [url] - a blank page with none - in a new tab: beside the one that opened it, or last; in front where [switch]. */
    fun newTab(url: String? = BlankPage, switch: Boolean = true, openedBy: String? = null): BrowserTab {
        val tab = BrowserTab(UUID.randomUUID().toString(), url, openedBy, shields)
        val position = openedBy?.let { id -> tabs.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) } ?: tabs.size
        tabs.add(position, tab)
        activeIndex = if (switch) position else activeIndex + (if (position <= activeIndex) 1 else 0)
        return tab
    }

    fun closeTab(tab: BrowserTab) {
        val index = tabs.indexOf(tab).takeIf { it >= 0 } ?: return
        tabs.removeAt(index)
        shields.forget(tab.visitedHosts - tabs.flatMapTo(HashSet()) { it.visitedHosts })
        tab.webView?.destroy()
        tab.webView = null
        activeIndex = when {
            tabs.isEmpty() -> -1
            index < activeIndex || activeIndex >= tabs.size -> (activeIndex - 1).coerceAtLeast(0)
            else -> activeIndex
        }
    }

    fun switchTo(tab: BrowserTab) {
        tabs.indexOf(tab).takeIf { it >= 0 }?.let { activeIndex = it }
    }

    /** Every web view let go - as the browser closes. */
    fun releaseAll() {
        shields.forget(tabs.flatMapTo(HashSet()) { it.visitedHosts })
        tabs.forEach { tab ->
            tab.webView?.destroy()
            tab.webView = null
        }
        // Cookies refused to a page are no one else's to be refused - the downloads' own sign-in pages.
        CookieManager.getInstance().setAcceptCookie(true)
    }

    /**
     * What is typed in the address field, as a page to load: an address as it is, a site's given its scheme - else a search.
     * Words with a space between them are always a search.
     */
    fun pageFor(typed: String): String {
        val text = typed.trim()
        return when {
            ' ' in text -> searchFor(text)
            isWebAddress(text) -> text
            WebsiteAndTld.matches(text) && isWebAddress("https://$text") -> "https://$text"
            else -> searchFor(text)
        }
    }

    private fun searchFor(text: String) = searchEngine().queryUrl + URLEncoder.encode(text, Charsets.UTF_8.name())

    /** [tab]'s web view, made the first time it is shown - and resumed, as it comes back in front. */
    @SuppressLint("SetJavaScriptEnabled")
    internal fun webViewOf(tab: BrowserTab, context: Context): WebView {
        tab.webView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            existing.onResume()
            return existing
        }
        return WebView(context).apply {
            // Filling what it is given, as a browser's page does: left to wrap its content, a web view's viewport has no height,
            // and a page sized by it - `100vh`, `100dvh`, as a video player's overlay is - is drawn nothing at all.
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // A page opening a window - a player, a download's own page - opens a tab: see onCreateWindow.
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            // A secure page's video served over plain HTTP still plays, as it would in any browser.
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.mediaPlaybackRequiresUserGesture = false
            // The Shields' script, first in every frame: farbling, Global Privacy Control, what is hidden, de-AMP.
            addJavascriptInterface(shields.Bridge(), shields.bridgeName)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(this, shields.pageScript, setOf("*"))
            }
            webViewClient = TabClient(tab)
            webChromeClient = TabChromeClient(tab)
            setFindListener { active, count, isDone -> if (isDone) tab.foundMatches = (if (count == 0) 0 else active + 1) to count }
            isLongClickable = true
            setOnLongClickListener {
                val hit = hitTestResult
                val link = hit.extra.takeIf { hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE }
                link?.let { onLinkHeld(HeldLink(it, tab)) } != null
            }
            setDownloadListener { url, userAgent, _, _, _ ->
                // A window opened for nothing but the download has nothing else to show.
                if (!canGoBack() && originalUrl == null && tab.openedBy != null) closeTab(tab)
                download(url, userAgent, originalUrl ?: tab.openerPage)
            }
            tab.webView = this
            tab.firstPage?.let(tab::load)
        }
    }

    /** [tab]'s web view, paused as another tab comes in front - its media stops, as a browser's background tab's does. */
    internal fun pause(tab: BrowserTab) {
        tab.webView?.onPause()
    }

    /**
     * [url], downloaded as the page asked: with what its request carried where it was seen, [userAgent], the browser's cookies
     * for it and [page] it came from as its referrer - none of which a site that serves files only to its pages would do without.
     */
    fun download(url: String, userAgent: String?, page: String?) {
        if (!isWebAddress(url)) return
        val now = System.currentTimeMillis()
        requests.entries.removeIf { now - it.value.first > RememberedRequestMs }
        val headers = requests[url]?.second.orEmpty().filterKeys { it.lowercase() !in RequestOnlyHeaders }.toMutableMap()
        fun putIfAbsent(name: String, value: String?) {
            if (value != null && headers.keys.none { it.equals(name, ignoreCase = true) }) headers[name] = value
        }
        putIfAbsent("User-Agent", userAgent)
        putIfAbsent("Referer", page?.takeIf(::isWebAddress))
        CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let { cookies ->
            val key = headers.keys.firstOrNull { it.equals("Cookie", ignoreCase = true) }
            if (key == null) headers["Cookie"] = cookies else headers[key] = "${headers[key]}; $cookies"
        }
        onDownload(CaughtDownload(url, headers))
    }

    private inner class TabClient(private val tab: BrowserTab) : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            val host = url?.let(Uri::parse)?.host?.lowercase()
            if (host != null && url.startsWith("http")) {
                tab.goingTo(view, host)
                tab.visitedHosts += host
            }
            tab.blockedCount = 0
            tab.progress = 0f
            tab.title = null
            tab.icon = favicon
            // The blank page is home's - no page, as the bar shows it.
            tab.url = url?.takeUnless { it == BlankPage }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            tab.progress = null
            // Its site's cookies, signed in or not, are downloads' to sign in with - and the settings' to list.
            url?.let(cookies::remember)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            tab.url = url?.takeUnless { it == BlankPage }
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        // Every request the page makes is seen - on the web view's own thread - so a download it becomes goes as the page asked.
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            val headers = request.requestHeaders.orEmpty()
            val host = request.url.host?.lowercase()
            val page = tab.pageHost
            if (!request.isForMainFrame && host != null && page != null) {
                val type = requestTypeOf(request.url, headers, isMainFrame = false)
                val filterRequest = FilterRequest(url, host, page, type)
                if (shields.shouldBlock(filterRequest)) {
                    view.post { tab.blockedCount++ }
                    return shields.blockedResponse(filterRequest)
                }
            }
            requests[url] = System.currentTimeMillis() to headers
            return null
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) httpsFailed(view, request.url)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            httpsFailed(view, Uri.parse(error.url))
        }

        /** [url] failed - where it was asked over HTTPS in HTTP's place, its HTTP page instead, or the warning before it. */
        private fun httpsFailed(view: WebView, url: Uri) {
            if (url.scheme != "https" || url.host?.lowercase() != tab.upgradedHost?.lowercase()) return
            tab.upgradedHost = null
            val insecure = shields.fallBackFrom(url)
            if (insecure != null) view.loadUrl(insecure.toString()) else showHttpsWarning(view, url)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith(ShieldsScheme)) {
                // The HTTPS warning's own links: on to the page over HTTP, or back from it.
                val target = request.url.getQueryParameter("url")?.let(Uri::parse)
                if (target != null) shields.insecure(target)?.let { view.loadUrl(it.toString()) } else if (view.canGoBack()) view.goBack()
                return true
            }
            if (url.startsWith("http://") || url.startsWith("https://")) return tab.takesOver(view, request)
            // An app's own link opens the app - or, where it is not installed, the page the link falls back to.
            runCatching {
                if (url.startsWith("intent://")) {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    if (intent.resolveActivity(view.context.packageManager) != null) {
                        view.context.startActivity(intent)
                    } else {
                        intent.getStringExtra("browser_fallback_url")?.let(view::loadUrl)
                    }
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    if (intent.resolveActivity(view.context.packageManager) != null) view.context.startActivity(intent)
                }
            }
            return true
        }
    }

    /**
     * Whether a page's navigation to [request] is taken over: a link through a tracker's redirect gone straight past it, one
     * carrying trackers' parameters cleaned of them, HTTP asked over HTTPS - its scripts and cookies readied as its Shields
     * allow, where it goes on as it is.
     */
    private fun BrowserTab.takesOver(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (!request.isForMainFrame || request.method != "GET") return false
        val host = uri.host?.lowercase()
        // A window opened by a tap, its first page one the lists' `$popup` filters block - a popunder - closed at once.
        if (openedBy != null && !isPopupChecked) {
            isPopupChecked = true
            if (shields.blocksPopup(uri, openerPage?.let(Uri::parse)?.host)) {
                view.post { closeTab(this) }
                return true
            }
        }
        // A site sending its HTTPS page back to HTTP has none to be upgraded to.
        if (request.isRedirect && uri.scheme == "http" && host != null && host == upgradedHost?.lowercase()) {
            upgradedHost = null
            if (shields.fallBackFrom(uri) == null) {
                showHttpsWarning(view, uri.buildUpon().scheme("https").build())
                return true
            }
            goingTo(view, host)
            return false
        }
        if (!request.isRedirect) upgradedCount = 0
        val cleaned = shields.cleaned(uri, fromHost = pageHost)
        val upgraded = shields.upgraded(cleaned ?: uri)
        if (upgraded != null) {
            upgradedHost = upgraded.host
            upgradedCount++
        }
        val target = upgraded ?: cleaned
        goingTo(view, (target ?: uri).host)
        if (target == null) return false
        view.loadUrl(target.toString())
        return true
    }

    /** Brave's strict upgrade's warning: [url]'s site has no HTTPS page - go on over HTTP, or back. */
    private fun showHttpsWarning(view: WebView, url: Uri) {
        val context = view.context
        val insecure = url.buildUpon().scheme("http").build().toString()
        val proceed = "$ShieldsScheme//proceed?url=" + URLEncoder.encode(insecure, Charsets.UTF_8.name())
        fun text(id: Int, vararg args: Any) = TextUtils.htmlEncode(context.getString(id, *args))
        val html = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>" +
            "body{margin:0;padding:48px 24px;font:16px/1.5 sans-serif;color-scheme:light dark}h1{font-size:1.4em}" +
            "a{display:inline-block;margin:16px 16px 0 0;font-weight:bold}</style></head><body>" +
            "<h1>${text(R.string.browser_https_warning_title)}</h1><p>${text(R.string.browser_https_warning_body, url.host.orEmpty())}</p>" +
            "<a href=\"$ShieldsScheme//back\">${text(R.string.browser_https_warning_back)}</a>" +
            "<a href=\"${TextUtils.htmlEncode(proceed)}\">${text(R.string.browser_https_warning_proceed)}</a></body></html>"
        // Kept in the history as the insecure page it stands before - never shown as a secure one.
        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", insecure)
    }

    private inner class TabChromeClient(private val tab: BrowserTab) : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String?) {
            tab.title = title
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            tab.icon = icon
        }

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (tab.progress != null) tab.progress = newProgress / 100f
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            // A window a page opens on its own, with no tap asking for it, is a popup - blocked, as Chromium's popup blocker does.
            if (!isUserGesture && shields.blocksUnaskedPopups(tab.pageHost)) {
                view.post { tab.blockedCount++ }
                return false
            }
            val opened = newTab(url = null, switch = true, openedBy = tab.id)
            opened.openerPage = view.originalUrl ?: view.url
            transport.webView = webViewOf(opened, view.context)
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            tabs.firstOrNull { it.webView === window }?.let(::closeTab)
        }

        // A video's full-screen button: the page's own player, over everything, until it is left.
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            fullScreen = FullScreenView(view) {
                fullScreen = null
                callback.onCustomViewHidden()
            }
        }

        override fun onHideCustomView() {
            fullScreen = null
        }
    }
}

/** Whether [text] is an address a web view loads: HTTP or HTTPS, with a host. */
private fun isWebAddress(text: String): Boolean =
    runCatching { Uri.parse(text) }.getOrNull()?.let { it.scheme in setOf("http", "https") && !it.host.isNullOrEmpty() } == true
