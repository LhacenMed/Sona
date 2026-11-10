package com.lhacenmed.sona.core.downloader.sites

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A web address a site reader may read or name: HTTPS on port 443, a host name rather than an address, no user name, and
 * written exactly as OkHttp would write it - so what is checked is what is requested.
 */
internal object HttpsUrl {
    private val literal = Regex("([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\\d.]+)")
    private val numericLabel = Regex("(?:[0-9]+|0[xX][0-9a-fA-F]+)")
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private const val Hex = "0123456789abcdefABCDEF"

    /** [raw] read as such an address, its fragment left out - null for anything else. */
    fun parse(raw: String): HttpUrl? {
        if (!isSafeText(raw)) return null
        val value = raw.substringBefore('#')
        if (!value.startsWith("https://", ignoreCase = true)) return null
        val authority = value.substringAfter("://").substringBefore('/').substringBefore('?')
        if ('@' in authority) return null
        val parsed = value.toHttpUrlOrNull() ?: return null
        if (!parsed.isHttps || parsed.port != 443 || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
        val host = parsed.host.trimEnd('.')
        if (literal.matches(host) || numericLabel.matches(host.substringAfterLast('.'))) return null
        val afterAuthority = value.substringAfter("://").drop(authority.length)
        val path = afterAuthority.substringBefore('?').ifEmpty { "/" }
        val query = if ('?' in afterAuthority) afterAuthority.substringAfter('?') else null
        if (path != parsed.encodedPath || query != parsed.encodedQuery) return null
        for (index in value.indices) {
            if (value[index] == '%' && (index + 2 >= value.length || value[index + 1] !in Hex || value[index + 2] !in Hex)) return null
        }
        return parsed
    }

    /** [location], as a redirect names it, read against [base]. */
    fun resolve(base: HttpUrl, location: String): HttpUrl? {
        if (!isSafeText(location)) return null
        val value = location.substringBefore('#')
        if (scheme.containsMatchIn(value)) return parse(value)
        if (value.startsWith("//")) return parse("https:$value")
        return base.resolve(value)?.let { parse(it.toString()) }
    }

    private fun isSafeText(raw: String): Boolean = raw.length in 1..8192 && raw.none {
        it <= ' ' || Character.isISOControl(it) || it == '\\' || Character.getType(it) == Character.FORMAT.toInt() || Character.isSurrogate(it)
    }
}
