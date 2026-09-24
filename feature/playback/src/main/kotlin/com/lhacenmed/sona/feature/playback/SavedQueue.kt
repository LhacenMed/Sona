package com.lhacenmed.sona.feature.playback

import androidx.media3.common.MediaItem
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.database.dao.QueueItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The queue saved when the app last closed, as the player takes it back: its tracks still in the
 * library, the one that was playing, where it stopped, and the order it was shuffled in.
 *
 * Read the same way whichever brings it back - the app opening, or a media button waking the service
 * with no app around - so both play on from exactly the same place.
 */
internal class SavedQueue(
    val mediaItems: List<MediaItem>,
    val currentIndex: Int,
    val startPositionMs: Long,
    /** Indices into [mediaItems] in the order they were shuffled, or null when none was kept. */
    val shuffleOrder: IntArray?,
)

/** The saved queue, or null when there is none or none of its tracks are in the library any more. */
internal suspend fun loadSavedQueue(queueItemDao: QueueItemDao, repository: LibraryRepository): SavedQueue? {
    val items = withContext(Dispatchers.IO) { queueItemDao.getAll() }
    if (items.isEmpty()) return null
    // Track ids are derived from file paths and so survive a rescan. Before that, a scan
    // reassigned every id, and a restored queue silently resolved to nothing after the
    // first relaunch.
    val tracksById = withContext(Dispatchers.IO) {
        repository.tracksByIds(items.map { it.trackId }).associateBy { it.id }
    }
    val restored = items.mapNotNull { queueItem ->
        tracksById[queueItem.trackId]?.let { track -> queueItem to track }
    }
    if (restored.isEmpty()) return null
    val currentIndex = restored.indexOfFirst { it.first.isCurrent }.takeIf { it >= 0 } ?: 0
    // Tracks gone from the library leave gaps in the saved positions, so the order is rebuilt from
    // the positions the rest still hold. A queue saved before positions were kept has them all equal.
    val shufflePositions = restored.map { it.first.shufflePosition }
    val shuffleOrder = if (shufflePositions.distinct().size == restored.size) {
        restored.indices.sortedBy { shufflePositions[it] }.toIntArray()
    } else {
        null
    }
    return SavedQueue(
        mediaItems = restored.map { it.second.toMediaItem() },
        currentIndex = currentIndex,
        startPositionMs = restored[currentIndex].first.lastPositionMs,
        shuffleOrder = shuffleOrder,
    )
}
