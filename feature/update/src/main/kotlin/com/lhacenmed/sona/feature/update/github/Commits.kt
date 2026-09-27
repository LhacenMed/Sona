package com.lhacenmed.sona.feature.update.github

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONArray

/** A commit on the development branch - ArchiveTune's `GitCommit`. */
@Immutable
data class Commit(
    /** The short hash. */
    val sha: String,
    /** The subject line. */
    val message: String,
    val author: String,
    /** When it was authored, as ISO 8601. */
    val date: String,
    val url: String,
    val authorAvatarUrl: String?,
)

/**
 * What is coming next: the development branch's latest commits - ArchiveTune's `getCommitHistory`. Kept on
 * the device between asks - see [CachedGitHubResource].
 */
object Commits {
    private const val Count = 30

    private val resource =
        CachedGitHubResource("commits", "/commits?sha=${GitHub.DEVELOPMENT_BRANCH}&per_page=$Count", ::parse)

    /** The kept commits, or null when none have been kept. */
    fun cached(context: Context): List<Commit>? = resource.cached(context)

    /** The latest commits - asking GitHub now when [forceRefresh]. */
    suspend fun recent(context: Context, forceRefresh: Boolean = false): Result<List<Commit>> =
        runCatchingCancellable { resource.fetch(context, forceRefresh) }

    private fun parse(json: String): List<Commit> {
        val array = JSONArray(json)
        return (0 until array.length()).map(array::getJSONObject).map { item ->
            val commit = item.getJSONObject("commit")
            val author = commit.optJSONObject("author")
            Commit(
                sha = item.optString("sha").take(7),
                message = commit.optString("message").lineSequence().firstOrNull().orEmpty(),
                author = author?.optString("name")?.takeIf { it.isNotBlank() } ?: "Unknown",
                date = author?.optString("date").orEmpty(),
                url = item.optString("html_url"),
                authorAvatarUrl = item.optJSONObject("author")?.optString("avatar_url")?.takeIf { it.isNotBlank() },
            )
        }
    }
}
