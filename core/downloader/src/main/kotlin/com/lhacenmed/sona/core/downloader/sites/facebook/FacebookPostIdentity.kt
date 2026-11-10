package com.lhacenmed.sona.core.downloader.sites.facebook

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import okhttp3.HttpUrl

internal enum class FacebookLinkKind { SHARE, SHORTLINK, STORY, PERMALINK, POST, PHOTO, REEL, WATCH, VIDEO }

/**
 * A public Facebook link of the shapes read, made www.facebook.com's with only its identifying query kept. A share link or
 * an fb.watch link names nothing until its redirect does. An id is a node's only where it is numeric: a pfbid token is
 * never compared with one.
 */
internal class FacebookPostIdentity private constructor(
    val kind: FacebookLinkKind,
    val fetchUrl: String,
    val postId: String?,
    val photoId: String?,
    val videoId: String?,
    /** A `/share/v/` or `/share/r/` link: a video's, or a reel's. */
    val isVideoShare: Boolean = false,
) {
    val isShortlink: Boolean get() = kind == FacebookLinkKind.SHARE || kind == FacebookLinkKind.SHORTLINK

    /** The page is the post or photo itself, not a video's or a reel's. */
    val isPostOrPhoto: Boolean get() = kind in setOf(FacebookLinkKind.STORY, FacebookLinkKind.PERMALINK, FacebookLinkKind.POST,
        FacebookLinkKind.PHOTO, FacebookLinkKind.SHARE)

    /** [fetchUrl] on [host]: web.facebook.com, in regions that serve it, rather than www. */
    fun fetchUrlOn(host: String): String = if (host == "web.facebook.com") fetchUrl.replaceFirst(Www, "https://web.facebook.com") else fetchUrl

    companion object {
        private const val Www = "https://www.facebook.com"
        private val hosts = setOf("www.facebook.com", "facebook.com", "m.facebook.com", "web.facebook.com")
        /** Mobile and locale hosts serve the same posts; what they name is read on www. */
        private val inputHosts = hosts + setOf("mbasic.facebook.com", "touch.facebook.com", "mobile.facebook.com")
        private val localeHost = Regex("[a-z]{2}-[a-z]{2}\\.facebook\\.com")
        private val token = Regex("[A-Za-z0-9_-]{1,64}")
        private val numeric = Regex("[0-9]{1,25}")
        private val storyId = Regex("[0-9]{1,25}|pfbid[A-Za-z0-9]{1,80}")
        private val segment = Regex("[A-Za-z0-9._-]{1,100}")
        private val share = Regex("/share/(?:([pvr])/)?(${token.pattern})/?")
        private val shortlink = Regex("/(${token.pattern})/?")
        private val posts = Regex("/(?:groups/)?(${segment.pattern})/(?:posts|permalink)/(?:[^/]{1,512}/)?([0-9]{1,25}|pfbid[A-Za-z0-9]{1,80})/?")
        private val photos = Regex("/(${segment.pattern})/photos/(?:[a-z]{1,8}\\.[0-9]{1,25}/)?([0-9]{1,25})/?")
        private val reel = Regex("/reel/([0-9]{1,25})/?")
        private val group = Regex("/groups/(${segment.pattern})/?")
        private val videos = Regex("/(${segment.pattern})/videos/(?:[^/]{1,512}/)?([0-9]{1,25})/?")
        private val loginPaths = setOf("/login", "/login/", "/login.php", "/checkpoint", "/checkpoint/")

        fun parse(url: String): FacebookPostIdentity? {
            if ('#' in url) return null
            val parsed = HttpsUrl.parse(url) ?: return null
            val path = parsed.encodedPath
            if (parsed.host.lowercase() == "fb.watch") {
                return shortlink.matchEntire(path)?.let { FacebookPostIdentity(FacebookLinkKind.SHORTLINK, "https://fb.watch$path", null, null, null) }
            }
            if (!isFacebookHost(parsed)) return null
            if (path.startsWith("/events/") || path.startsWith("/stories/")) return null
            share.matchEntire(path)?.let {
                return FacebookPostIdentity(FacebookLinkKind.SHARE, Www + path, null, null, null, it.groupValues[1] in setOf("v", "r"))
            }
            when (path) {
                "/story.php", "/permalink.php" -> {
                    val story = single(parsed, "story_fbid", storyId) ?: return null
                    val owner = single(parsed, "id", numeric) ?: return null
                    val kind = if (path == "/story.php") FacebookLinkKind.STORY else FacebookLinkKind.PERMALINK
                    return FacebookPostIdentity(kind, "$Www$path?story_fbid=$story&id=$owner", story.takeIf(numeric::matches), null, null)
                }
                "/photo", "/photo/", "/photo.php" -> {
                    val photo = single(parsed, "fbid", numeric) ?: return null
                    return FacebookPostIdentity(FacebookLinkKind.PHOTO, "$Www/photo/?fbid=$photo", null, photo, null)
                }
                "/watch", "/watch/" -> {
                    val video = single(parsed, "v", numeric) ?: return null
                    return FacebookPostIdentity(FacebookLinkKind.WATCH, "$Www/watch/?v=$video", null, null, video)
                }
            }
            reel.matchEntire(path)?.let { return FacebookPostIdentity(FacebookLinkKind.REEL, Www + path, null, null, it.groupValues[1]) }
            photos.matchEntire(path)?.let { return FacebookPostIdentity(FacebookLinkKind.PHOTO, Www + path, null, it.groupValues[2], null) }
            videos.matchEntire(path)?.let { return FacebookPostIdentity(FacebookLinkKind.VIDEO, Www + path, null, null, it.groupValues[2]) }
            // A group's feed opened on one post is that post.
            group.matchEntire(path)?.let { match ->
                val id = single(parsed, "multi_permalinks", numeric) ?: return null
                return FacebookPostIdentity(FacebookLinkKind.POST, "$Www/groups/${match.groupValues[1]}/posts/$id/", id, null, null)
            }
            posts.matchEntire(path)?.let { match ->
                val id = match.groupValues[2]
                return FacebookPostIdentity(FacebookLinkKind.POST, Www + path, id.takeIf(numeric::matches), null, null)
            }
            return null
        }

        fun isFacebookHost(url: HttpUrl): Boolean = url.host.lowercase().let { it in inputHosts || localeHost.matches(it) }

        /** A story, which always asks to sign in. */
        fun isStoryLink(url: String): Boolean = HttpsUrl.parse(url)?.let { isFacebookHost(it) && it.encodedPath.startsWith("/stories/") } == true

        /** The logged-out wall on any Facebook host. */
        fun isLoginWall(url: HttpUrl): Boolean = isFacebookHost(url) && url.encodedPath in loginPaths

        private fun single(url: HttpUrl, name: String, grammar: Regex): String? =
            url.queryParameterValues(name).singleOrNull()?.takeIf { grammar.matches(it) }
    }
}
