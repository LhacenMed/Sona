package com.lhacenmed.sona.core.downloader.sites.x

import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import org.json.JSONObject

/** The most of a post's syndication JSON read. */
private const val MaxBytes = 512 * 1024

/** X, read from a post's public syndication JSON - one request, no account - by [XVideoInfo]. */
internal object XReader : SiteReader {
    override fun accepts(url: String): Boolean = XPostIdentity.parse(url) != null

    override fun read(url: String, http: SiteHttp): String? {
        val identity = XPostIdentity.parse(url) ?: return null
        val page = http.get(identity.syndicationUrl, MaxBytes, SiteHttp.Json) ?: return null
        val root = runCatching { JSONObject(page.text) }.getOrNull() ?: return null
        return XVideoInfo.build(root, identity.postId, identity.mediaIndex, identity.canonicalUrl ?: url)
    }
}
