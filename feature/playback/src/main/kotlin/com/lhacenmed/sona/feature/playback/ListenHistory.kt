package com.lhacenmed.sona.feature.playback

import androidx.media3.common.MediaItem
import com.lhacenmed.sona.core.database.dao.PlayStatsDao
import com.lhacenmed.sona.core.vault.VaultRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where every listen [PlayHistoryRecorder] notes is written down, by the [PlaybackSpace] it was heard in: the
 * library's in its play statistics - Recent and Most played - and the Private Folder's as each item's play count,
 * kept in the vault with the rest of it so nothing of it reaches the library's database or a backup.
 */
@Singleton
class ListenHistory @Inject constructor(
    private val playStatsDao: PlayStatsDao,
    private val vault: VaultRepository,
) {
    /** [item] has started playing - which only the library keeps, for Recent: the folder has none. */
    internal suspend fun recordStarted(item: MediaItem, playedAt: Long) {
        if (item.space != PlaybackSpace.Library) return
        item.idInSpace?.let { playStatsDao.recordPlayStarted(it, playedAt) }
    }

    /** [item] has been listened to long enough to count. */
    internal suspend fun recordCounted(item: MediaItem, playedAt: Long) {
        val id = item.idInSpace ?: return
        when (item.space) {
            PlaybackSpace.Library -> playStatsDao.recordPlayCounted(id, playedAt)
            PlaybackSpace.Private -> vault.countPlay(id)
        }
    }
}
