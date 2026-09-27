package com.lhacenmed.sona.feature.update.github

import android.content.Context
import androidx.core.content.edit

/**
 * One of GitHub's answers the app keeps - the releases, the commits and the contributors each have one:
 * the body GitHub last sent for [path], its ETag, and when it was last asked for.
 *
 * A screen opens on the kept answer at once, offline included. GitHub is asked again whenever the app
 * wants the answer current - as it opens, as the device comes back online, as a screen opens - but not
 * within [RefreshIntervalMillis] of the last time unless the user asks outright: an app without a token
 * has sixty requests an hour, and GitHub counts a 304 against them too. It asks with the last ETag, so an
 * unchanged answer is not sent again.
 */
internal class CachedGitHubResource<T>(
    name: String,
    private val path: String,
    private val parse: (String) -> T,
) {
    private val bodyKey = "${name}_body"
    private val etagKey = "${name}_etag"
    private val checkedAtKey = "${name}_checked_at"

    /** The kept answer, or null when there is none. */
    fun cached(context: Context): T? =
        prefs(context).getString(bodyKey, null)?.let { runCatching { parse(it) }.getOrNull() }

    /**
     * The answer as it stands: the kept one when it was asked for within [RefreshIntervalMillis] and not
     * [force]d, GitHub's otherwise. Unreachable or refused, the kept one still stands - no longer
     * [Fetched.isCurrent], so a caller that needs today's answer can tell and ask again; with none kept, it
     * fails.
     */
    suspend fun fetch(context: Context, force: Boolean): Fetched<T> {
        val prefs = prefs(context)
        val body = prefs.getString(bodyKey, null)
        val now = System.currentTimeMillis()
        if (body != null && !force && now - prefs.getLong(checkedAtKey, 0L) < RefreshIntervalMillis) {
            return Fetched(parse(body), isCurrent = true)
        }

        val etag = prefs.getString(etagKey, null).takeIf { body != null }
        val response = runCatchingCancellable { GitHub.get("${GitHub.API_URL}$path", etag) }.getOrNull()
        return when {
            response?.isNotModified == true && body != null -> {
                prefs.edit { putLong(checkedAtKey, now) }
                Fetched(parse(body), isCurrent = true)
            }
            response?.isSuccessful == true && response.body != null -> {
                // Read before it is kept, so an answer that cannot be read never replaces one that can.
                val answer = parse(response.body)
                prefs.edit {
                    putString(bodyKey, response.body)
                    putString(etagKey, response.etag)
                    putLong(checkedAtKey, now)
                }
                Fetched(answer, isCurrent = true)
            }
            body != null -> Fetched(parse(body), isCurrent = false)
            else -> error(response?.let { "GitHub answered HTTP ${it.status}" } ?: "Could not reach GitHub")
        }
    }

    private companion object {
        const val PREFS = "github_cache"
        const val RefreshIntervalMillis = 15 * 60 * 1000L

        fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}

/**
 * A [CachedGitHubResource]'s answer, and whether it [isCurrent]: GitHub's own, or kept and asked for within the
 * refresh interval - rather than kept from before and stood in for a GitHub that could not be reached.
 */
internal class Fetched<T>(val value: T, val isCurrent: Boolean)
