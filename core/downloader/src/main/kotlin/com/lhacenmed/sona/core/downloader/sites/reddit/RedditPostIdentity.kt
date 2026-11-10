package com.lhacenmed.sona.core.downloader.sites.reddit

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import okhttp3.HttpUrl

/**
 * A public Reddit post link and the one www page that reads it, its query left out. A post link keeps its slug: a slugless
 * `/r/<sub>/comments/<id>/` is a shell without the post, so it reads `/comments/<id>/`, which Reddit redirects to the whole
 * permalink. A share link (`/s/`) and a video link (`v.redd.it`) name no post until their redirect does.
 */
internal class RedditPostIdentity private constructor(val fetchUrl: String, val postId: String?, val videoId: String?) {
    companion object {
        private val hosts = setOf("reddit.com", "www.reddit.com", "old.reddit.com", "new.reddit.com", "m.reddit.com", "np.reddit.com", "sh.reddit.com")
        private const val Name = "[A-Za-z0-9_-]{1,64}"
        private const val Id = "[a-z0-9]{1,16}"
        private val post = Regex("/(?:(?:r|user|u)/($Name)/)?comments/($Id)(?:/([A-Za-z0-9_%-]{1,300}))?(?:/[a-z0-9]{1,16})?/?")
        private val share = Regex("/(?:r|u|user)/$Name/s/[A-Za-z0-9]{1,32}/?")
        private val video = Regex("/($Id)/?")
        private val videoPage = Regex("/video/($Id)/?")
        private val gallery = Regex("/gallery/($Id)/?")
        private val loginWall = Regex("/(?:login|account/login|register)/?")

        fun parse(raw: String): RedditPostIdentity? = HttpsUrl.parse(raw)?.let(::parse)

        fun parse(url: HttpUrl): RedditPostIdentity? {
            val path = url.encodedPath
            return when (url.host) {
                "redd.it" -> video.matchEntire(path)?.let { post(null, it.groupValues[1], null) }
                "v.redd.it" -> video.matchEntire(path)?.let { videoPage(it.groupValues[1]) }
                in hosts -> post.matchEntire(path)?.let { match ->
                    val (owner, id, slug) = match.destructured
                    // A user's own post keeps its path; Reddit redirects it as it needs.
                    val kind = path.substringAfter('/').substringBefore('/')
                    post(owner.takeIf { it.isNotEmpty() }?.let { "$kind/$it" }, id, slug.takeIf { it.isNotEmpty() })
                } ?: gallery.matchEntire(path)?.let { post(null, it.groupValues[1], null) }
                    ?: share.matchEntire(path)?.let { RedditPostIdentity("https://www.reddit.com$path".trimEnd('/') + "/", null, null) }
                    ?: videoPage.matchEntire(path)?.let { videoPage(it.groupValues[1]) }
                else -> null
            }
        }

        private fun post(owner: String?, id: String, slug: String?) = RedditPostIdentity(
            if (owner != null && slug != null) "https://www.reddit.com/$owner/comments/$id/$slug/" else "https://www.reddit.com/comments/$id/",
            id,
            null,
        )

        private fun videoPage(id: String) = RedditPostIdentity("https://www.reddit.com/video/$id/", null, id)

        /** The post a redirect or a page names, in its www form - null for anything else. */
        fun postHop(url: HttpUrl): RedditPostIdentity? = parse(url)?.takeIf { it.postId != null && url.host in hosts }

        /** Reddit's sign-in wall, never followed. */
        fun isLoginWall(url: HttpUrl): Boolean = url.host in hosts && loginWall.matches(url.encodedPath)
    }
}
