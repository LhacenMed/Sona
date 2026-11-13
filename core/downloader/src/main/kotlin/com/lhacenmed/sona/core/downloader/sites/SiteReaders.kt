package com.lhacenmed.sona.core.downloader.sites

import com.lhacenmed.sona.core.downloader.sites.bluesky.BlueskyReader
import com.lhacenmed.sona.core.downloader.sites.facebook.FacebookReader
import com.lhacenmed.sona.core.downloader.sites.instagram.InstagramReader
import com.lhacenmed.sona.core.downloader.sites.linkedin.LinkedInReader
import com.lhacenmed.sona.core.downloader.sites.pinterest.PinterestReader
import com.lhacenmed.sona.core.downloader.sites.reddit.RedditReader
import com.lhacenmed.sona.core.downloader.sites.snapchat.SnapchatReader
import com.lhacenmed.sona.core.downloader.sites.telegram.TelegramReader
import com.lhacenmed.sona.core.downloader.sites.threads.ThreadsReader
import com.lhacenmed.sona.core.downloader.sites.tiktok.TikTokReader
import com.lhacenmed.sona.core.downloader.sites.x.XReader
import okhttp3.HttpUrl

/** Every site read by the app itself, and which one reads a link. */
internal object SiteReaders {
    val all: List<SiteReader> = listOf(
        XReader,
        RedditReader,
        InstagramReader,
        TikTokReader,
        ThreadsReader,
        BlueskyReader,
        PinterestReader,
        FacebookReader,
        LinkedInReader,
        TelegramReader,
        SnapchatReader,
        // Last: a site's own reader knows its links better than their extension does.
        DirectMediaReader,
    )

    /** The reader of [url] - null where no site here reads it, decided from the link alone. */
    fun readerFor(url: String): SiteReader? = all.firstOrNull { runCatching { it.accepts(url) }.getOrDefault(false) }
}

/**
 * Links that only redirect: where one leads is known from its redirect alone, which [SiteHttp.landing] follows without
 * reading a page. A reader takes those of its own site.
 */
internal object ShortLinks {
    private val hosts = setOf("pin.it", "vm.tiktok.com", "vt.tiktok.com")
    private val instagramHosts = setOf("www.instagram.com", "instagram.com")
    private val token = Regex("/[A-Za-z0-9_-]{1,64}/?")
    private val instagramShare = Regex("/share/(?:reel/|p/)?[A-Za-z0-9_-]{1,64}/?")
    private val pinterestRedirect = Regex("/url_shortener/[A-Za-z0-9_-]{1,64}/redirect/?")

    fun isShortLink(url: HttpUrl): Boolean = when (url.host) {
        in hosts -> token.matches(url.encodedPath)
        "api.pinterest.com" -> pinterestRedirect.matches(url.encodedPath)
        else -> isInstagramShare(url)
    }

    /** Instagram's `/share/…` link, which names a share rather than its post. */
    fun isInstagramShare(url: HttpUrl): Boolean = url.host in instagramHosts && instagramShare.matches(url.encodedPath)
}
