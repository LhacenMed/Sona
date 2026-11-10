package com.lhacenmed.sona.core.downloader.sites.linkedin

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import okhttp3.HttpUrl

/**
 * A public LinkedIn post, from the link shapes read. A `lnkd.in/p/` short link names no post until its redirect does. A post's
 * slug carries one typed id - `activity-`, `share-` or `ugcPost-` - and two links are one post when they agree on an id of a
 * kind both carry: a slug's words are not its identity. A share or ugcPost link and an activity link share no kind; only the
 * page's own post container can bridge them, never a redirect.
 */
internal class LinkedInPostIdentity private constructor(val isShortlink: Boolean, val slug: String?, private val ids: Map<String, String>) {
    val activityId: String? get() = ids[Activity]

    /** Never true of a short link. Shared typed ids must all agree; without one, the slugs must. */
    fun sameAs(other: LinkedInPostIdentity): Boolean {
        if (isShortlink || other.isShortlink) return false
        val shared = ids.keys intersect other.ids.keys
        if (shared.isNotEmpty()) return shared.all { ids[it] == other.ids[it] }
        return slug != null && slug == other.slug
    }

    /** The post's clean public link - no query, a trailing slash - null for a short link. */
    val cleanUrl: String? get() = when {
        isShortlink -> null
        slug != null -> "https://www.linkedin.com/posts/$slug/"
        else -> (activityId?.let { Activity to it } ?: ids.entries.firstOrNull()?.toPair())
            ?.let { (kind, id) -> "https://www.linkedin.com/feed/update/urn:li:$kind:$id/" }
    }

    /** Whether [other] names a post by another kind of id only, so [sameAs] can neither prove nor refute them one post. */
    fun sharesNoIdKind(other: LinkedInPostIdentity): Boolean = !isShortlink && !other.isShortlink &&
        ids.isNotEmpty() && other.ids.isNotEmpty() && (ids.keys intersect other.ids.keys).isEmpty()

    /** The slug's author and title words before its typed id. */
    val vanity: String? get() = slug?.let { text ->
        slugIds.findAll(text).toList().singleOrNull()?.range?.first?.let { text.substring(0, it) }?.takeIf(String::isNotEmpty)
    }

    fun matchesActivity(id: String): Boolean = !isShortlink && activityId != null && activityId == id

    /** Whether [attributedUrn], `urn:li:share:N` or `urn:li:ugcPost:N`, names this post's own typed id. */
    fun matchesAttributed(attributedUrn: String): Boolean {
        val match = attributed.matchEntire(attributedUrn.trim()) ?: return false
        return !isShortlink && ids[match.groupValues[1]] == match.groupValues[2]
    }

    /** This post with the activity id its page's own container evidences; one it has is never replaced. */
    fun withBridgedActivity(id: String): LinkedInPostIdentity =
        if (activityId != null) this else LinkedInPostIdentity(isShortlink, slug, ids + (Activity to id))

    companion object {
        /** A slug as LinkedIn serves it: `[A-Za-z0-9_-]` and escapes, never of `#`, `%`, `.`, `/`, `?`, `\` or a control. */
        private const val Slug = "(?:[A-Za-z0-9_-]|%(?![01][0-9A-Fa-f]|2[35EeFf]|3[Ff]|5[Cc]|7[Ff])[0-9A-Fa-f]{2})+"
        private const val Activity = "activity"
        private val post = Regex("/posts/($Slug)/?")
        private val update = Regex("(?:/embed)?/feed/update/urn:li:(activity|share|ugcPost):([0-9]+)/?")
        private val encodedColon = Regex("%3[Aa]")
        private val slugIds = Regex("(?:^|[_-])(activity|share|ugcPost)-([0-9]+)(?:-|$)")
        private val urn = Regex("urn:li:activity:([0-9]+)")
        private val attributed = Regex("urn:li:(share|ugcPost):([0-9]+)")
        private val loginWall = Regex("/(?:signup(?:/[a-z-]+)?|authwall|login|uas/login|checkpoint/[a-z/-]+)/?")

        fun parse(url: String): LinkedInPostIdentity? {
            val parsed = HttpsUrl.parse(url) ?: return null
            if (!isPageEntry(parsed)) return null
            if (parsed.host == "lnkd.in") return LinkedInPostIdentity(true, null, emptyMap())
            post.matchEntire(parsed.encodedPath)?.let { match ->
                val slug = match.groupValues[1]
                // Exactly one typed id in the slug; several carry none.
                val typed = slugIds.findAll(slug).toList().singleOrNull()
                return LinkedInPostIdentity(false, slug, typed?.let { mapOf(it.groupValues[1] to it.groupValues[2]) } ?: emptyMap())
            }
            update.matchEntire(updatePath(parsed.encodedPath))?.let {
                return LinkedInPostIdentity(false, null, mapOf(it.groupValues[1] to it.groupValues[2]))
            }
            return null
        }

        fun isPost(url: HttpUrl): Boolean = url.host == "www.linkedin.com" &&
            (Regex("/posts/$Slug/?").matches(url.encodedPath) || Regex("/feed/update/urn:li:(?:activity|share|ugcPost):[0-9]+/?").matches(url.encodedPath))

        fun isEntryHost(host: String): Boolean = host == "linkedin.com" || host == "www.linkedin.com" || Regex("[a-z]{2}\\.linkedin\\.com").matches(host)

        /** www's host; an `/embed/` or `%3A`-escaped update link as its plain `/feed/update/urn:li:…/` page. */
        fun normalizeEntry(url: HttpUrl): HttpUrl {
            if (!isEntryHost(url.host)) return url
            val path = updatePath(url.encodedPath).removePrefix("/embed").takeIf { it.startsWith("/feed/update/") } ?: url.encodedPath
            return url.newBuilder().host("www.linkedin.com").encodedPath(path).build()
        }

        fun isPageEntry(url: HttpUrl): Boolean = isEntryHost(url.host) && isPost(normalizeEntry(url)) ||
            (url.host == "lnkd.in" && Regex("/p/[A-Za-z0-9_-]+/?").matches(url.encodedPath))

        /** LinkedIn's sign-up or sign-in wall, never followed. */
        fun isLoginWall(url: HttpUrl): Boolean = isEntryHost(url.host) && loginWall.matches(url.encodedPath)

        /** A www redirect's target in its plain form; another host's is kept as sent, and refused. */
        fun hopTarget(to: HttpUrl): HttpUrl = if (to.host == "www.linkedin.com") normalizeEntry(to) else to

        /** Whether a redirect from [from] to [to] keeps to one post: only a short link may say which post it is. */
        fun allowsHop(from: HttpUrl, to: HttpUrl): Boolean {
            val hop = hopTarget(to)
            if (!isPageEntry(from) || !isPost(hop)) return false
            if (from.host == "lnkd.in") return true
            val source = parse(from.toString()) ?: return false
            val target = parse(hop.toString()) ?: return false
            return source.sameAs(target)
        }

        /** An update path with only `%3A` read as `:`; any other escape stays and fails the match. */
        private fun updatePath(encodedPath: String): String =
            if (encodedPath.contains("/feed/update/")) encodedPath.replace(encodedColon, ":") else encodedPath

        fun activityUrn(value: String): String? = urn.matchEntire(value.trim())?.groupValues?.get(1)
    }
}
