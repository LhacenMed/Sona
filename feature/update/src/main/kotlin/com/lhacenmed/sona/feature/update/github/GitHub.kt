package com.lhacenmed.sona.feature.update.github

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where Sona lives on GitHub, and the one way the app asks it anything. */
object GitHub {
    const val OWNER = "LhacenMed"
    const val REPOSITORY = "Sona"
    const val REPOSITORY_URL = "https://github.com/$OWNER/$REPOSITORY"
    const val RELEASES_URL = "$REPOSITORY_URL/releases"
    const val ISSUES_URL = "$REPOSITORY_URL/issues"
    const val CONTRIBUTORS_URL = "$REPOSITORY_URL/graphs/contributors"
    const val PROFILE_URL = "https://github.com/$OWNER"
    const val AVATAR_URL = "https://github.com/$OWNER.png"

    /** Where commits land before a release takes them to main. */
    const val DEVELOPMENT_BRANCH = "dev"

    internal const val API_URL = "https://api.github.com/repos/$OWNER/$REPOSITORY"

    private const val TIMEOUT_MS = 15_000

    /** A response: its status, its body - none for 304 Not Modified - and the ETag to ask with next time. */
    internal class Response(val status: Int, val body: String?, val etag: String?) {
        val isSuccessful: Boolean get() = status in 200..299
        val isNotModified: Boolean get() = status == HttpURLConnection.HTTP_NOT_MODIFIED
    }

    /**
     * GETs [url] from GitHub's API. Given the [etag] of an earlier answer, GitHub answers 304 when nothing
     * has changed - which it does not count against the hourly limit an app without a token is held to.
     */
    internal suspend fun get(url: String, etag: String? = null): Response = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", REPOSITORY)
            if (!etag.isNullOrBlank()) setRequestProperty("If-None-Match", etag)
        }
        try {
            val status = connection.responseCode
            val body = if (status in 200..299) connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) } else null
            Response(status, body, connection.getHeaderField("ETag"))
        } finally {
            connection.disconnect()
        }
    }
}

/** [block]'s result, or its failure - a cancellation is left to cancel. */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * What the app keeps of GitHub's answers, so a screen opens on the last ones at once, offline included,
 * and the network is asked again only when they are old.
 */
internal object GitHubCache {
    private const val PREFS = "github_cache"

    fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
