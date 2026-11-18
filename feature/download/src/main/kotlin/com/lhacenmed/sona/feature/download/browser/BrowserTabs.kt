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

/** Where a new tab opens with no page asked for. */
private const val BlankPage = "about:blank"

/** How long a page's request is remembered, for its headers to go with a download it turns out to be. */
private const val RememberedRequestMs = 20_000L

/** Headers a page's request carries that are its own to that one request, never a download's. */
private val RequestOnlyHeaders = setOf("range", "if-range", "if-none-match", "if-modified-since")

/** A site's address typed alone - `example.com`, `example.co.uk:8080` - with no scheme before it. */
private val WebsiteAndTld = Regex("""^[\w.-]+\.[a-zA-Z]{2,}(:\d{1,5})?$""")

/** What the browser searches with, for what is typed that is no address. */
private const val SearchEngine = "https://duckduckgo.com/?q="

/**
 * One tab: its own web view - made as it is first shown, kept while the tab is open, paused while another is in front - and
 * what it shows, as the bar reads it.
 */
@Stable
class BrowserTab internal constructor(val id: String, internal val firstPage: String?, internal val openedBy: String?) {
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

    /** The page that opened this tab as a new window, where one did - a download it starts came from there. */
    internal var openerPage: String? = null

    fun load(url: String) {
        webView?.loadUrl(url)
    }

    fun goBack() {
        webView?.goBack()
    }

    fun goForward() {
        webView?.goForward()
    }

    fun reload() {
        webView?.reload()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }
}

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
        val tab = BrowserTab(UUID.randomUUID().toString(), url, openedBy)
        val position = openedBy?.let { id -> tabs.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) } ?: tabs.size
        tabs.add(position, tab)
        activeIndex = if (switch) position else activeIndex + (if (position <= activeIndex) 1 else 0)
        return tab
    }

    fun closeTab(tab: BrowserTab) {
        val index = tabs.indexOf(tab).takeIf { it >= 0 } ?: return
        tabs.removeAt(index)
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
        tabs.forEach { tab ->
            tab.webView?.destroy()
            tab.webView = null
        }
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

    private fun searchFor(text: String) = SearchEngine + URLEncoder.encode(text, Charsets.UTF_8.name())

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
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = TabClient(tab)
            webChromeClient = TabChromeClient(tab)
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
            tab.firstPage?.let(::loadUrl)
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
            tab.progress = 0f
            tab.title = null
            tab.icon = favicon
            tab.url = url
        }

        override fun onPageFinished(view: WebView, url: String?) {
            tab.progress = null
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            tab.url = url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        // Every request the page makes is seen - on the web view's own thread - so a download it becomes goes as the page asked.
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            requests[request.url.toString()] = System.currentTimeMillis() to request.requestHeaders.orEmpty()
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) return false
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
