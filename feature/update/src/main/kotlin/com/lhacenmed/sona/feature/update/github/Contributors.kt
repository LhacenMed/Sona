package com.lhacenmed.sona.feature.update.github

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import org.json.JSONArray

/** Someone who has contributed to Sona's repository - ArchiveTune's `AboutContributor`. */
@Immutable
data class Contributor(
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
)

/**
 * The repository's contributors, bots left out - ArchiveTune's `AboutContributorsRepository`: fetched once
 * and kept, since who has contributed changes rarely and About should open on it at once.
 */
object Contributors {
    private const val Limit = 20
    private const val KEY_JSON = "contributors_json"

    suspend fun all(context: Context): Result<List<Contributor>> = runCatchingCancellable {
        val prefs = GitHubCache.prefs(context)
        val cached = prefs.getString(KEY_JSON, null)?.let(::parse).orEmpty()
        if (cached.isNotEmpty()) return@runCatchingCancellable cached

        val response = GitHub.get("${GitHub.API_URL}/contributors?per_page=$Limit")
        val body = response.body ?: error("GitHub answered HTTP ${response.status}")
        parse(body).also { contributors ->
            if (contributors.isNotEmpty()) prefs.edit { putString(KEY_JSON, body) }
        }
    }

    private fun parse(json: String): List<Contributor> {
        val array = JSONArray(json)
        return (0 until array.length()).map(array::getJSONObject)
            .filterNot { it.optString("type").equals("Bot", ignoreCase = true) || it.optString("login").endsWith("[bot]") }
            .map { Contributor(it.optString("login"), it.optString("avatar_url"), it.optString("html_url")) }
            .filter { it.login.isNotBlank() && it.avatarUrl.isNotBlank() }
            .take(Limit)
    }
}
