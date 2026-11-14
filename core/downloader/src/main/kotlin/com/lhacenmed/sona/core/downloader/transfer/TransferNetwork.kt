package com.lhacenmed.sona.core.downloader.transfer

import com.lhacenmed.sona.core.model.DownloaderPreferences
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import kotlin.math.pow
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okio.Throttler

/** A size with yt-dlp's suffixes - `500K`, `4.2M`, `1GiB` - read in 1024s; a plain number is bytes. */
private val SizeWithSuffix = Regex("""(?i)^\s*(\d+(?:\.\d+)?)\s*([kmgt]?)i?b?\s*$""")

/**
 * [base], making its requests as [preferences] say yt-dlp's are made: through their proxy - AB Download Manager's manual proxy,
 * an HTTP one signed in to with what its address carries - so a direct download goes where the downloader's would.
 */
internal fun DownloaderPreferences.transferClient(base: OkHttpClient): OkHttpClient {
    val address = proxy.trim().takeIf { it.isNotEmpty() } ?: return base
    val uri = URI(if ("://" in address) address else "http://$address")
    val isSocks = uri.scheme.orEmpty().lowercase().startsWith("socks")
    val type = if (isSocks) Proxy.Type.SOCKS else Proxy.Type.HTTP
    val port = uri.port.takeIf { it > 0 } ?: if (isSocks) 1080 else 80
    val credentials = uri.rawUserInfo?.takeIf { !isSocks }?.let { userInfo ->
        Credentials.basic(userInfo.substringBefore(':').decoded(), userInfo.substringAfter(':', "").decoded())
    }
    return base.newBuilder()
        .proxy(Proxy(type, InetSocketAddress.createUnresolved(uri.host, port)))
        .apply {
            credentials?.let { signIn ->
                proxyAuthenticator { _, response -> response.request.newBuilder().header("Proxy-Authorization", signIn).build() }
            }
        }
        .build()
}

/**
 * What holds a download to [preferences]' limit - every connection of it at once, so the limit is the download's, as
 * yt-dlp's `--limit-rate` is - null with no limit.
 */
internal fun DownloaderPreferences.rateThrottler(): Throttler? =
    bytesPerSecondOf(rateLimit)?.let { limit -> Throttler().apply { bytesPerSecond(limit) } }

/** [text] - `2M`, `500K` - in bytes, as yt-dlp reads a rate: null where it is blank, or no size. */
internal fun bytesPerSecondOf(text: String): Long? {
    val (number, suffix) = SizeWithSuffix.matchEntire(text)?.destructured ?: return null
    val power = if (suffix.isEmpty()) 0 else "kmgt".indexOf(suffix.lowercase()) + 1
    return (number.toDouble() * 1024.0.pow(power)).toLong().takeIf { it > 0 }
}

private fun String.decoded(): String = java.net.URLDecoder.decode(this, "UTF-8")
