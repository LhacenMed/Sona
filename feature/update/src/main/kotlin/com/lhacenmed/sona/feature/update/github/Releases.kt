package com.lhacenmed.sona.feature.update.github

import android.content.Context
import androidx.core.content.edit
import com.lhacenmed.sona.core.datastore.UpdateChannel
import org.json.JSONArray

/**
 * Sona's releases, from GitHub - ArchiveTune's `Updater`, over one repository whose pre-releases are the
 * beta channel.
 *
 * Kept on the device between asks: a screen opens on the last list at once, offline too, and GitHub is
 * asked again only once it is [MaxAgeMillis] old - or when the user asks outright - and then with the last
 * answer's ETag, so an unchanged list costs nothing.
 */
object Releases {
    private const val MaxAgeMillis = 6 * 60 * 60 * 1000L
    private const val PageSize = 30

    private const val KEY_JSON = "releases_json"
    private const val KEY_ETAG = "releases_etag"
    private const val KEY_CHECKED_AT = "releases_checked_at"

    /** The kept releases on [channel], newest first; empty when none have been kept. */
    fun cached(context: Context, channel: UpdateChannel): List<Release> = readCache(context).on(channel)

    /** The releases on [channel], newest first: the kept ones while fresh, GitHub's otherwise - or when [forceRefresh]. */
    suspend fun all(context: Context, channel: UpdateChannel, forceRefresh: Boolean = false): Result<List<Release>> =
        runCatchingCancellable { fetch(context, forceRefresh).on(channel) }

    /** The newest release on [channel]. */
    suspend fun latest(context: Context, channel: UpdateChannel, forceRefresh: Boolean = false): Result<Release> =
        all(context, channel, forceRefresh).mapCatching { it.firstOrNull() ?: error("No releases found") }

    private suspend fun fetch(context: Context, forceRefresh: Boolean): List<Release> {
        val prefs = GitHubCache.prefs(context)
        val cached = readCache(context)
        val now = System.currentTimeMillis()
        val isFresh = now - prefs.getLong(KEY_CHECKED_AT, 0L) < MaxAgeMillis
        if (cached.isNotEmpty() && isFresh && !forceRefresh) return cached

        val etag = prefs.getString(KEY_ETAG, null).takeIf { cached.isNotEmpty() }
        val response = runCatchingCancellable { GitHub.get("${GitHub.API_URL}/releases?per_page=$PageSize", etag) }.getOrNull()
        return when {
            response?.isNotModified == true -> {
                prefs.edit { putLong(KEY_CHECKED_AT, now) }
                cached
            }
            response?.isSuccessful == true && response.body != null -> {
                val releases = Release.listFromGitHub(response.body)
                prefs.edit {
                    putString(KEY_JSON, JSONArray(releases.map(Release::toJson)).toString())
                    putString(KEY_ETAG, response.etag)
                    putLong(KEY_CHECKED_AT, now)
                }
                releases
            }
            // Unreachable, or refused: the last answer still stands, when there is one.
            cached.isNotEmpty() -> cached
            else -> error(response?.let { "GitHub answered HTTP ${it.status}" } ?: "Could not reach GitHub")
        }
    }

    private fun readCache(context: Context): List<Release> =
        GitHubCache.prefs(context).getString(KEY_JSON, null)?.let { json ->
            runCatching {
                val array = JSONArray(json)
                (0 until array.length()).map { Release.fromJson(array.getJSONObject(it)) }
            }.getOrNull()
        }.orEmpty()

    /** The releases a channel offers, newest version first. */
    private fun List<Release>.on(channel: UpdateChannel): List<Release> =
        filter { channel == UpdateChannel.BETA || !it.isPreRelease }
            .sortedWith(
                compareByDescending<Release, SemanticVersion?>(nullsFirst()) { it.version }
                    .thenByDescending { it.publishedAt },
            )
}
