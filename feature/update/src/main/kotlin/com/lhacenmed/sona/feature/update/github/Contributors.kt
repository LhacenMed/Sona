package com.lhacenmed.sona.feature.update.github

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONArray

/** Someone who has contributed to Sona's repository - ArchiveTune's `AboutContributor`. */
@Immutable
data class Contributor(
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
)

/**
 * The repository's contributors, bots left out - ArchiveTune's `AboutContributorsRepository`. Kept on the
 * device between asks - see [CachedGitHubResource].
 */
object Contributors {
    private const val Limit = 20

    private val resource = CachedGitHubResource("contributors", "/contributors?per_page=$Limit", ::parse)

    /** The kept contributors, or null when none have been kept. */
    fun cached(context: Context): List<Contributor>? = resource.cached(context)

    /** The contributors - asking GitHub now when [forceRefresh]. */
    suspend fun all(context: Context, forceRefresh: Boolean = false): Result<List<Contributor>> =
        runCatchingCancellable { resource.fetch(context, forceRefresh) }

    private fun parse(json: String): List<Contributor> {
        val array = JSONArray(json)
        return (0 until array.length()).map(array::getJSONObject)
            .filterNot { it.optString("type").equals("Bot", ignoreCase = true) || it.optString("login").endsWith("[bot]") }
            .map { Contributor(it.optString("login"), it.optString("avatar_url"), it.optString("html_url")) }
            .filter { it.login.isNotBlank() && it.avatarUrl.isNotBlank() }
            .take(Limit)
    }
}
