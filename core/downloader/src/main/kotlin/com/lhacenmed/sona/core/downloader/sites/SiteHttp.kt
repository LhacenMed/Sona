package com.lhacenmed.sona.core.downloader.sites

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

/**
 * The network as one site read sees it: HTTPS to public addresses alone - every host name looked up is checked, so a link
 * cannot reach the device's own network - with no cookie kept, no proxy, a redirect followed only where the reader says
 * where to, and every answer held to a size. A read is let make [MaxRequests] requests, redirects included, within
 * [DeadlineMs] of its first: one [SiteHttp] is made for each read. Every call blocks its thread.
 */
internal class SiteHttp(private val client: OkHttpClient = sharedClient) {
    private var requests = 0
    private var startedAt = 0L

    /** A page as answered: the address it was answered from, its text, and the cookies the answer set. */
    class Page(val url: HttpUrl, val text: String, val cookies: Map<String, String>)

    /**
     * [url], answered with 200 in at most [maxBytes] - null for any other answer, or one past the read's budget. A redirect
     * is followed only to what [follow] makes of it, and at most [MaxRedirects] of them.
     */
    fun get(
        url: String,
        maxBytes: Int,
        headers: Map<String, String>,
        follow: (from: HttpUrl, to: HttpUrl) -> HttpUrl? = { _, _ -> null },
    ): Page? {
        var current = HttpsUrl.parse(url) ?: return null
        val seen = hashSetOf(current.toString())
        repeat(MaxRedirects + 1) {
            val request = Request.Builder().url(current).apply { headers.forEach { (name, value) -> header(name, value) } }.build()
            val answered = execute(request) { response ->
                when {
                    response.code in Redirects -> Hop(response.header("Location")?.let { redirectTarget(current, it) } ?: return@execute null)
                    response.code != 200 -> null
                    else -> {
                        val body = response.body ?: return@execute null
                        if (body.contentLength() > maxBytes) return@execute null
                        val source = body.source()
                        // More than the cap buffered: the page is not one this read takes.
                        if (source.request(maxBytes + 1L)) return@execute null
                        val cookies = response.headers("Set-Cookie").mapNotNull { cookie ->
                            cookie.substringBefore(';').split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }
                                ?.let { (name, value) -> name.trim() to value.trim() }
                        }.toMap()
                        Page(current, source.buffer.readUtf8(), cookies)
                    }
                }
            } ?: return null
            when (answered) {
                is Page -> return answered
                is Hop -> {
                    val next = follow(current, answered.to) ?: return null
                    if (!seen.add(next.toString())) return null
                    current = next
                }
            }
        }
        return null
    }

    /**
     * Where the short link [url] leads: its redirects followed, never a page read, until the first that [isShortLink] does not
     * call one - which is answered without being requested. Null where the link leads nowhere this way.
     */
    fun landing(url: String, isShortLink: (HttpUrl) -> Boolean): HttpUrl? {
        var current = HttpsUrl.parse(url)?.takeIf(isShortLink) ?: return null
        repeat(MaxRedirects) {
            // Shorteners answer a browser with a page of their own, and anything else with the redirect.
            val request = Request.Builder().url(current).header("User-Agent", "Sona").build()
            val next = execute(request) { response ->
                if (response.code !in Redirects) return@execute null
                val location = response.header("Location") ?: return@execute null
                // A shortener's plain-HTTP destination is read over HTTPS, as a pasted one would be.
                redirectTarget(current, location) ?: redirectTarget(current, location.replaceFirst(PlainHttp, "https://"))
            } ?: return null
            if (!isShortLink(next)) return next
            current = next
        }
        return null
    }

    private fun <T> execute(request: Request, answer: (okhttp3.Response) -> T?): T? {
        val now = System.currentTimeMillis()
        if (requests == 0) startedAt = now
        val remaining = DeadlineMs - (now - startedAt)
        if (requests >= MaxRequests || remaining <= 0) return null
        requests++
        val call = client.newCall(request)
        call.timeout().timeout(minOf(remaining, CallTimeoutMs), TimeUnit.MILLISECONDS)
        return try {
            call.execute().use(answer)
        } catch (_: IOException) {
            null
        }
    }

    private class Hop(val to: HttpUrl)

    /** A redirect's [location], read against [base]: its raw UTF-8, as some sites send a title in it, written as escapes. */
    private fun redirectTarget(base: HttpUrl, location: String): HttpUrl? {
        val ascii = buildString {
            var index = 0
            while (index < location.length) {
                val codePoint = location.codePointAt(index)
                if (codePoint < 0x80) appendCodePoint(codePoint)
                else String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).forEach { append("%%%02X".format(it.toInt() and 0xFF)) }
                index += Character.charCount(codePoint)
            }
        }
        return HttpsUrl.resolve(base, ascii)
    }

    companion object {
        const val MaxRequests = 8
        const val DeadlineMs = 20_000L
        const val MaxRedirects = 4
        private const val CallTimeoutMs = 10_000L

        /** The desktop browser the pages that ask for one are read as - and their videos fetched as. */
        const val DesktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        private const val MobileUserAgent = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        /** A page read as a desktop browser opening it: Facebook answers anything less with an error, others with an empty page. */
        val BrowserPage = mapOf(
            "User-Agent" to DesktopUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-User" to "?1",
            "Upgrade-Insecure-Requests" to "1",
        )

        /** A Threads post page: it serves its post's data only to what looks like a document being opened. */
        val ThreadsPage = mapOf(
            "User-Agent" to MobileUserAgent,
            "Accept" to "text/html",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
        )

        /** A page read as a phone's browser. */
        val MobilePage = mapOf("User-Agent" to MobileUserAgent, "Accept" to "text/html,application/xhtml+xml;q=0.9")

        val Json = mapOf("User-Agent" to MobileUserAgent, "Accept" to "application/json")

        /** A file a page names that is not a page - a manifest. */
        val File = mapOf("User-Agent" to MobileUserAgent, "Accept" to "*/*")

        private val Redirects = setOf(301, 302, 303, 307, 308)
        private val PlainHttp = Regex("^http://", RegexOption.IGNORE_CASE)

        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .dns(PublicAddressDns)
                .protocols(listOf(Protocol.HTTP_1_1))
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
                .cookieJar(CookieJar.NO_COOKIES)
                .authenticator(Authenticator.NONE)
                .proxyAuthenticator(Authenticator.NONE)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** Host names looked up as usual, and refused where any address they name is not a public one. */
private object PublicAddressDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val answers = Dns.SYSTEM.lookup(hostname)
        if (answers.isEmpty() || answers.size > 16 || !answers.all(PublicAddresses::allows)) throw UnknownHostException("Not a public address")
        return answers
    }
}

/**
 * Which addresses are public: everything but IANA's special-purpose ranges - the device itself, its local network, carrier
 * NAT, link-local, documentation, benchmarking, multicast and reserved space - and, of IPv6, only global unicast that maps
 * to none of them.
 */
internal object PublicAddresses {
    private val deniedIpv4 = listOf(
        0x00000000L to 8, 0x0a000000L to 8, 0x64400000L to 10, 0x7f000000L to 8,
        0xa9fe0000L to 16, 0xac100000L to 12, 0xc0000000L to 24, 0xc0000200L to 24,
        0xc0586300L to 24, 0xc0a80000L to 16, 0xc6120000L to 15, 0xc6336400L to 24,
        0xcb007100L to 24, 0xe0000000L to 4, 0xf0000000L to 4,
    )

    fun allows(address: InetAddress): Boolean = allows(address.address)

    private fun allows(bytes: ByteArray): Boolean {
        if (bytes.size == 4) {
            val value = bytes.fold(0L) { number, byte -> (number shl 8) or (byte.toLong() and 255) }
            return deniedIpv4.none { (prefix, bits) -> value and ((0xffffffffL shl (32 - bits)) and 0xffffffffL) == prefix }
        }
        if (bytes.size != 16) return false
        val b = bytes.map { it.toInt() and 255 }
        // An IPv4 address written as IPv6, or behind the well-known NAT64 prefix, is the IPv4 address.
        if (b.take(10).all { it == 0 } && b[10] == 255 && b[11] == 255) return allows(bytes.copyOfRange(12, 16))
        if (b[0] == 0 && b[1] == 0x64 && b[2] == 0xff && b[3] == 0x9b && b.subList(4, 12).all { it == 0 }) return allows(bytes.copyOfRange(12, 16))
        if (b[0] and 0xe0 != 0x20) return false // Only global unicast, 2000::/3.
        if (b[0] == 0x20 && b[1] == 1 && b[2] and 0xfe == 0) return false // 2001::/23
        if (b.take(4) == listOf(0x20, 1, 0x0d, 0xb8)) return false // 2001:db8::/32
        if (b[0] == 0x20 && b[1] == 2) return false // 6to4, 2002::/16
        if (b[0] == 0x3f && b[1] == 0xff && b[2] and 0xf0 == 0) return false // 3fff::/20
        return true
    }
}
