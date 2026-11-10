package com.lhacenmed.sona.core.downloader.sites.x

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl

/**
 * A public X post link: `x.com`, `twitter.com` or a mirror's `/<user>/status/<id>` or `/i/status/<id>`, with the viewer's
 * `/photo/<n>` or `/video/<n>` or a mirror's `/<lang>` after it. Only the post's id is kept - the user and any query are
 * never requested.
 */
internal class XPostIdentity private constructor(
    val postId: String,
    /** The post's own x.com link where the user's is a mirror's - which yt-dlp does not read. */
    val canonicalUrl: String?,
    /** The `/photo|video/<n>` of the user's own link, which is the media yt-dlp reads of it. */
    val mediaIndex: Int?,
) {
    /** The post's public syndication JSON: no account, no cookie. */
    val syndicationUrl: String get() = "https://cdn.syndication.twimg.com/tweet-result?id=$postId&token=${token(postId)}"

    companion object {
        private val mirrors = setOf("fxtwitter.com", "vxtwitter.com", "fixupx.com", "fixvx.com", "d.fxtwitter.com")
        private val hosts = setOf("x.com", "www.x.com", "mobile.x.com", "m.x.com", "twitter.com", "www.twitter.com",
            "mobile.twitter.com", "m.twitter.com", "d.fxtwitter.com", "fxtwitter.com", "www.fxtwitter.com", "vxtwitter.com",
            "www.vxtwitter.com", "fixupx.com", "www.fixupx.com", "fixvx.com", "www.fixvx.com")
        private val path = Regex("/(?:i(?:/web)?|[A-Za-z0-9_]{1,15})/status/([1-9][0-9]{0,18})(?:/(?:photo|video)/([1-4]))?/?")
        /** A mirror's translated view, `/status/<id>/<lang>`. */
        private val translated = Regex("(/(?:i(?:/web)?|[A-Za-z0-9_]{1,15})/status/[1-9][0-9]{0,18})/[a-z]{2}/?")

        fun parse(url: String): XPostIdentity? {
            val parsed = HttpsUrl.parse(url) ?: return null
            val host = parsed.host.lowercase()
            if (host !in hosts) return null
            val mirror = host.removePrefix("www.") in mirrors
            val encodedPath = if (mirror) translated.matchEntire(parsed.encodedPath)?.groupValues?.get(1) ?: parsed.encodedPath else parsed.encodedPath
            val match = path.matchEntire(encodedPath) ?: return null
            val id = match.groupValues[1]
            id.toLongOrNull() ?: return null
            return XPostIdentity(id, if (mirror) "https://x.com/i/status/$id" else null, if (mirror) null else match.groupValues[2].toIntOrNull())
        }

        /**
         * The syndication token: `id / 1e15 * π` written in base 36 as JavaScript's `Number.prototype.toString(36)` writes it,
         * then every `0` and `.` removed.
         */
        fun token(id: String): String = jsRadixString(id.toDouble() / 1e15 * Math.PI, 36).replace("0", "").replace(".", "")

        /** V8's `DoubleToRadixCString`, for a finite value of zero or more. */
        fun jsRadixString(input: Double, radix: Int): String {
            require(input.isFinite() && input >= 0 && radix in 2..36)
            val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
            var integer = Math.floor(input)
            var fraction = input - integer
            var delta = maxOf(0.5 * (Math.nextUp(input) - input), Math.nextUp(0.0))
            val digits = StringBuilder()
            if (fraction >= delta) {
                do {
                    fraction *= radix
                    delta *= radix
                    val digit = fraction.toInt()
                    digits.append(chars[digit])
                    fraction -= digit
                    if (fraction > 0.5 || (fraction == 0.5 && (digit and 1) == 1)) {
                        if (fraction + delta > 1) {
                            // Rounded up: the carry runs back through the digits written.
                            while (true) {
                                if (digits.isEmpty()) {
                                    integer += 1
                                    break
                                }
                                val last = chars.indexOf(digits[digits.length - 1])
                                digits.setLength(digits.length - 1)
                                if (last + 1 < radix) {
                                    digits.append(chars[last + 1])
                                    break
                                }
                            }
                            break
                        }
                    }
                } while (fraction >= delta)
            }
            val whole = StringBuilder()
            // Past 2^53 the low digits a double cannot hold are written as zeros.
            while (Math.getExponent(integer / radix) - 52 > 0) {
                integer /= radix
                whole.append('0')
            }
            do {
                val remainder = integer % radix
                whole.append(chars[remainder.toInt()])
                integer = (integer - remainder) / radix
            } while (integer > 0)
            whole.reverse()
            return if (digits.isEmpty()) whole.toString() else "$whole.$digits"
        }
    }
}
