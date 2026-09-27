package com.lhacenmed.sona.feature.update.github

import android.content.Context
import com.lhacenmed.sona.core.datastore.UpdateChannel

/**
 * Sona's releases, from GitHub - ArchiveTune's `Updater`, over one repository whose pre-releases are the
 * Artifact channel. Kept on the device between asks - see [CachedGitHubResource].
 */
object Releases {
    private const val PageSize = 30

    private val resource = CachedGitHubResource("releases", "/releases?per_page=$PageSize", Release::listFromGitHub)

    /** The kept releases on [channel], newest first; empty when none have been kept. */
    fun cached(context: Context, channel: UpdateChannel): List<Release> = resource.cached(context).orEmpty().on(channel)

    /**
     * The releases on [channel], newest first - asking GitHub now when [forceRefresh]. Unreachable, the kept
     * ones stand in: what a screen showing them wants.
     */
    suspend fun all(context: Context, channel: UpdateChannel, forceRefresh: Boolean = false): Result<List<Release>> =
        runCatchingCancellable { resource.fetch(context, forceRefresh).value.on(channel) }

    /**
     * The newest release on [channel] as GitHub has it now - asking it now when [forceRefresh]. Unlike [all],
     * it fails when GitHub cannot be reached rather than answer with what was kept, so a check can tell it
     * has not been answered and ask again.
     */
    internal suspend fun latest(context: Context, channel: UpdateChannel, forceRefresh: Boolean = false): Result<Release> =
        runCatchingCancellable {
            val fetched = resource.fetch(context, forceRefresh)
            check(fetched.isCurrent) { "Could not reach GitHub" }
            fetched.value.on(channel).firstOrNull() ?: error("No releases found")
        }

    /** The releases a channel offers, newest version first. */
    private fun List<Release>.on(channel: UpdateChannel): List<Release> =
        filter { channel == UpdateChannel.ARTIFACT || !it.isPreRelease }
            .sortedWith(
                compareByDescending<Release, SemanticVersion?>(nullsFirst()) { it.version }
                    .thenByDescending { it.publishedAt },
            )
}
