package com.lhacenmed.sona.feature.update.github

import android.content.Context
import com.lhacenmed.sona.core.datastore.UpdateChannel

/**
 * Sona's releases, from GitHub - ArchiveTune's `Updater`, over one repository whose pre-releases are the
 * beta channel. Kept on the device between asks - see [CachedGitHubResource].
 */
object Releases {
    private const val PageSize = 30

    private val resource = CachedGitHubResource("releases", "/releases?per_page=$PageSize", Release::listFromGitHub)

    /** The kept releases on [channel], newest first; empty when none have been kept. */
    fun cached(context: Context, channel: UpdateChannel): List<Release> = resource.cached(context).orEmpty().on(channel)

    /** The releases on [channel], newest first - asking GitHub now when [forceRefresh]. */
    suspend fun all(context: Context, channel: UpdateChannel, forceRefresh: Boolean = false): Result<List<Release>> =
        runCatchingCancellable { resource.fetch(context, forceRefresh).on(channel) }

    /** The newest release on [channel]. */
    suspend fun latest(context: Context, channel: UpdateChannel, forceRefresh: Boolean = false): Result<Release> =
        all(context, channel, forceRefresh).mapCatching { it.firstOrNull() ?: error("No releases found") }

    /** The releases a channel offers, newest version first. */
    private fun List<Release>.on(channel: UpdateChannel): List<Release> =
        filter { channel == UpdateChannel.BETA || !it.isPreRelease }
            .sortedWith(
                compareByDescending<Release, SemanticVersion?>(nullsFirst()) { it.version }
                    .thenByDescending { it.publishedAt },
            )
}
