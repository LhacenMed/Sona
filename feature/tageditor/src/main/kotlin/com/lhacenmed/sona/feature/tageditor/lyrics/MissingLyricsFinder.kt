package com.lhacenmed.sona.feature.tageditor.lyrics

import android.content.Context
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.network.NetworkMonitor
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.datastore.LyricsSettings
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackSpace
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.tageditor.TrackTagsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Gives the tracks around the one playing the lyrics their files are missing - the playing track first, then the
 * next, then the previous - each looked for on the web as the tag editor looks, the best that fits written into
 * the file, where the player reads it from the moment it is there.
 *
 * Only while the user lets it, while the device is online, and while Sona may change files without asking:
 * elsewhere Android asks the user before every file, which nothing done unasked may do. Each track is looked for
 * once a run of the app, found or not; one that was skipped on the way - the queue moved on, the connection
 * dropped - is looked for the next time it comes round.
 */
@Singleton
class MissingLyricsFinder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackController: PlaybackController,
    private val libraryRepository: LibraryRepository,
    private val trackTagsRepository: TrackTagsRepository,
    private val networkMonitor: NetworkMonitor,
    private val lyricsSettings: LyricsSettings,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** The tracks looked for this run of the app - those given lyrics, and those for which none were found. */
    private val lookedForTrackIds = mutableSetOf<Long>()

    fun start() {
        scope.launch {
            combine(
                playbackController.playbackState.map(::tracksAroundPlaying).distinctUntilChanged(),
                networkMonitor.isOnline,
                lyricsSettings.findMissingLyrics.flow,
            ) { trackIds, isOnline, isEnabled -> if (isOnline && isEnabled) trackIds else emptyList() }
                .distinctUntilChanged()
                // Whatever the queue moved on from is let go for what it moved on to.
                .collectLatest { trackIds -> trackIds.forEach { findLyrics(it) } }
        }
    }

    /** The playing track, then the next and the previous in the order the queue plays. */
    private fun tracksAroundPlaying(state: PlaybackUiState): List<Long> {
        val index = state.currentQueueIndex
        if (state.space != PlaybackSpace.Library || index !in state.queue.indices) return emptyList()
        return listOf(index, index + 1, index - 1).mapNotNull { state.queue.getOrNull(it)?.trackId }.distinct()
    }

    private suspend fun findLyrics(trackId: Long) {
        if (trackId in lookedForTrackIds || !AppPermission.FILE_CHANGES.isGranted(context)) return
        // Videos are played for their sound, and are seldom songs to have lyrics for.
        val track = libraryRepository.tracksById.value[trackId]?.takeUnless { it.isVideo } ?: return
        try {
            if (trackTagsRepository.readLyrics(track).isBlank()) {
                val found = lyricsQueryOf(track)?.let { OnlineLyrics.best(it, track.durationMs) }
                if (found != null) trackTagsRepository.saveLyrics(track, found.lyrics.text)
            }
            lookedForTrackIds += trackId
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A file that cannot be written is not tried again this run.
            lookedForTrackIds += trackId
        }
    }
}
