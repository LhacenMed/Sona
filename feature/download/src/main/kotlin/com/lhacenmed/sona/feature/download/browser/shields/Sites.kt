package com.lhacenmed.sona.feature.download.browser.shields

import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * [host]'s site - its eTLD+1, by the Public Suffix List OkHttp carries: `example.co.uk` of `www.example.co.uk`. The host itself
 * where it has none - an address, `localhost`.
 */
internal fun siteOf(host: String): String {
    sites[host]?.let { return it }
    val site = "http://$host/".toHttpUrlOrNull()?.topPrivateDomain() ?: host
    // A page's requests go to a few dozen hosts, asked of again and again - each looked up once, not each time.
    if (sites.size > MaxCachedSites) sites.clear()
    sites[host] = site
    return site
}

private const val MaxCachedSites = 4096
private val sites = ConcurrentHashMap<String, String>()

/** Whether [host] is no site's on the open web - an address, this device, a local network's or Tor's - which HTTPS is never asked of. */
internal fun isLocalHost(host: String): Boolean =
    host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".onion") || host.endsWith(".lan") ||
        host.startsWith('[') || host.all { it.isDigit() || it == '.' } || '.' !in host

/** [host] as its site's filters name it: itself, each domain above it, and each as an entity's `name.*` - `google.*`. */
internal fun domainKeysOf(host: String): List<String> {
    val keys = ArrayList<String>()
    val suffix = siteOf(host).substringAfter('.', "")
    var current = host
    while (true) {
        keys += current
        if (suffix.isNotEmpty() && current.endsWith(".$suffix")) keys += current.removeSuffix(suffix) + "*"
        val dot = current.indexOf('.')
        if (dot < 0 || current.substring(dot + 1) == suffix) break
        current = current.substring(dot + 1)
    }
    return keys
}
