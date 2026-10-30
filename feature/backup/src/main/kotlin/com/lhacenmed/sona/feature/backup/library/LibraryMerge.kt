package com.lhacenmed.sona.feature.backup.library

import com.lhacenmed.sona.core.database.entity.ArrangementEntity
import com.lhacenmed.sona.core.database.entity.FavoriteCollectionEntity
import com.lhacenmed.sona.core.database.entity.PlayStatsEntity
import com.lhacenmed.sona.core.database.entity.PlaylistCoverSource
import com.lhacenmed.sona.core.database.entity.PlaylistEntity
import com.lhacenmed.sona.core.database.entity.PlaylistTrackEntity
import com.lhacenmed.sona.core.database.entity.ResumePositionEntity
import com.lhacenmed.sona.core.model.sort.SortableList
import com.lhacenmed.sona.core.model.withRenamedPlaylist
import com.lhacenmed.sona.feature.backup.BackupLibrary

/** What the device's library already holds, as far as merging a backup into it needs to know. */
data class LocalLibrary(
    val playlists: List<PlaylistEntity>,
    val arrangedLists: Set<Pair<SortableList, String>>,
    val playStats: Map<Long, PlayStatsEntity>,
    val resumePositionTrackIds: Set<Long>,
)

/** A playlist the device does not have, with the backup's copy of its cover image to give it, if any. */
data class NewPlaylist(
    val playlist: PlaylistEntity,
    val coverImage: String?,
)

/**
 * The rows a backup adds to the device's library - every one of them written as "keep what is there", so
 * merging the same backup twice adds nothing the second time.
 *
 * @property playlistIds every backed-up playlist's id, to the id it has on this device.
 * @property playlistsModifiedAt the device's playlists the backup also has, to the backup's last change of each.
 */
data class LibraryMergePlan(
    val newPlaylists: List<NewPlaylist>,
    val playlistTracks: List<PlaylistTrackEntity>,
    val playlistsModifiedAt: Map<Long, Long>,
    val favoriteCollections: List<FavoriteCollectionEntity>,
    val arrangements: List<ArrangementEntity>,
    val playStats: List<PlayStatsEntity>,
    val resumePositions: List<ResumePositionEntity>,
    val playlistIds: Map<Long, Long>,
)

/**
 * Merges [backup] into [local], deciding nothing by order of arrival - so it comes out the same however many
 * times it runs:
 * - a playlist is the device's one of the same name, or a new one, kept at its backed-up id where that is free;
 *   its tracks are added to whichever, each keeping the date it was added first;
 * - a favorite is added where it is not one already;
 * - a list's hand-made order is the device's where it has one, else the backup's;
 * - a play count is the higher of the two, and a last play the later - adding counts up would count the same
 *   listens twice when a backup comes back to the device it was made on;
 * - a resume position is the device's where it has one, else the backup's.
 *
 * Tracks are named in a backup by path; [trackIdOf] gives the id of the library track each now is, or null for
 * one this device does not have, which is left out.
 */
fun planLibraryMerge(backup: BackupLibrary, local: LocalLibrary, trackIdOf: (String) -> Long?): LibraryMergePlan {
    val localByName = local.playlists.associateBy { it.name }
    val takenIds = local.playlists.mapTo(HashSet()) { it.id }
    var nextFreeId = (takenIds + backup.playlists.map { it.id }).maxOrNull()?.plus(1) ?: 1L

    val playlistIds = HashMap<Long, Long>()
    val newPlaylists = mutableListOf<NewPlaylist>()
    val playlistsModifiedAt = HashMap<Long, Long>()
    for (playlist in backup.playlists) {
        val existing = localByName[playlist.name]
        if (existing != null) {
            playlistIds[playlist.id] = existing.id
            playlistsModifiedAt[existing.id] = playlist.modifiedAt
            continue
        }
        val id = if (takenIds.add(playlist.id)) playlist.id else nextFreeId++
        playlistIds[playlist.id] = id
        val hasImage = playlist.coverSource == PlaylistCoverSource.IMAGE && playlist.coverImage != null
        newPlaylists += NewPlaylist(
            playlist = PlaylistEntity(
                id = id,
                name = playlist.name,
                isBuiltIn = playlist.isBuiltIn,
                createdAt = playlist.createdAt,
                modifiedAt = playlist.modifiedAt,
                // Only with its image: one the backup could not carry falls back to the playlist's own tracks.
                coverSource = if (playlist.coverSource == PlaylistCoverSource.IMAGE && !hasImage) PlaylistCoverSource.STACKED else playlist.coverSource,
                coverTrackId = playlist.coverTrackPath?.let(trackIdOf),
            ),
            coverImage = playlist.coverImage.takeIf { hasImage },
        )
    }

    val playlistTracks = backup.playlists.flatMap { playlist ->
        playlist.tracks.mapNotNull { track ->
            trackIdOf(track.path)?.let { PlaylistTrackEntity(playlistIds.getValue(playlist.id), it, track.addedAt) }
        }
    }

    val arrangements = backup.arrangements
        .groupBy { it.list to it.instanceId.renamedInstance(it.list, playlistIds) }
        .filterKeys { it !in local.arrangedLists }
        .flatMap { (list, rows) ->
            rows.mapNotNull { row -> trackIdOf(row.path)?.let { ArrangementEntity(list.first, list.second, it, row.position) } }
                .distinctBy { it.trackId }
        }

    val playStats = backup.playStats.mapNotNull { stats ->
        val trackId = trackIdOf(stats.path) ?: return@mapNotNull null
        val existing = local.playStats[trackId]
        PlayStatsEntity(
            trackId = trackId,
            playCount = maxOf(stats.playCount, existing?.playCount ?: 0),
            lastPlayedAt = maxOf(stats.lastPlayedAt, existing?.lastPlayedAt ?: 0L),
        )
    }.distinctBy { it.trackId }

    val resumePositions = backup.resumePositions.mapNotNull { position ->
        trackIdOf(position.path)?.takeIf { it !in local.resumePositionTrackIds }?.let { ResumePositionEntity(it, position.positionMs) }
    }.distinctBy { it.trackId }

    return LibraryMergePlan(
        newPlaylists = newPlaylists,
        playlistTracks = playlistTracks,
        playlistsModifiedAt = playlistsModifiedAt,
        favoriteCollections = backup.favoriteCollections.map {
            FavoriteCollectionEntity(withRenamedPlaylist(it.collection, playlistIds), it.favoritedAt)
        },
        arrangements = arrangements,
        playStats = playStats,
        resumePositions = resumePositions,
        playlistIds = playlistIds,
    )
}

/** A playlist's hand-made order is kept under its id; any other list's under what it is named by. */
private fun String.renamedInstance(list: SortableList, playlistIds: Map<Long, Long>): String =
    if (list == SortableList.PLAYLIST_TRACKS) toLongOrNull()?.let(playlistIds::get)?.toString() ?: this else this
