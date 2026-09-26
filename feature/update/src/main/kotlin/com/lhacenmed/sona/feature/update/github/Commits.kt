package com.lhacenmed.sona.feature.update.github

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

/** What is coming next: the development branch's latest commits - ArchiveTune's `getCommitHistory`. */
object Commits {
    suspend fun recent(count: Int = 30): Result<List<Commit>> = runCatchingCancellable {
        val response = GitHub.get("${GitHub.API_URL}/commits?sha=${GitHub.DEVELOPMENT_BRANCH}&per_page=$count")
        val body = response.body ?: error("GitHub answered HTTP ${response.status}")
        val array = JSONArray(body)
        (0 until array.length()).map(array::getJSONObject).map { item ->
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
