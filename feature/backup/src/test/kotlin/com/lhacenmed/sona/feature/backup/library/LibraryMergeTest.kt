package com.lhacenmed.sona.feature.backup.library

import com.lhacenmed.sona.core.database.entity.ArrangementEntity
import com.lhacenmed.sona.core.database.entity.PlayStatsEntity
import com.lhacenmed.sona.core.database.entity.PlaylistCoverSource
import com.lhacenmed.sona.core.database.entity.PlaylistEntity
import com.lhacenmed.sona.core.model.sort.SortableList
import com.lhacenmed.sona.feature.backup.BackedUpArrangement
import com.lhacenmed.sona.feature.backup.BackedUpFavoriteCollection
import com.lhacenmed.sona.feature.backup.BackedUpPlayStats
import com.lhacenmed.sona.feature.backup.BackedUpPlaylist
import com.lhacenmed.sona.feature.backup.BackedUpPlaylistTrack
import com.lhacenmed.sona.feature.backup.BackupLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryMergeTest {

    private val trackIds = mapOf("/m/a.mp3" to 1L, "/m/b.mp3" to 2L)

    private fun playlist(id: Long, name: String, vararg paths: String, coverSource: PlaylistCoverSource = PlaylistCoverSource.STACKED, coverImage: String? = null) =
        BackedUpPlaylist(
            id = id,
            name = name,
            isBuiltIn = false,
            createdAt = 10L,
            modifiedAt = 20L,
            coverSource = coverSource,
            coverTrackPath = null,
            coverImage = coverImage,
            tracks = paths.map { BackedUpPlaylistTrack(it, addedAt = 30L) },
        )

    private fun backup(
        playlists: List<BackedUpPlaylist> = emptyList(),
        favorites: List<BackedUpFavoriteCollection> = emptyList(),
        arrangements: List<BackedUpArrangement> = emptyList(),
        playStats: List<BackedUpPlayStats> = emptyList(),
    ) = BackupLibrary(playlists, favorites, arrangements, playStats, resumePositions = emptyList(), media = emptyList())

    private fun local(playlists: List<PlaylistEntity> = emptyList(), playStats: List<PlayStatsEntity> = emptyList()) =
        LocalLibrary(playlists, arrangedLists = emptySet(), playStats = playStats.associateBy { it.trackId }, resumePositionTrackIds = emptySet())

    private fun localPlaylist(id: Long, name: String) = PlaylistEntity(id = id, name = name, createdAt = 0L, modifiedAt = 0L)

    @Test
    fun `a playlist of the same name is merged into the device's, keeping its id`() {
        val plan = planLibraryMerge(backup(listOf(playlist(7, "Road", "/m/a.mp3"))), local(listOf(localPlaylist(3, "Road"))), trackIds::get)

        assertEquals(emptyList<NewPlaylist>(), plan.newPlaylists)
        assertEquals(mapOf(7L to 3L), plan.playlistIds)
        assertEquals(listOf(3L to 1L), plan.playlistTracks.map { it.playlistId to it.trackId })
    }

    @Test
    fun `a new playlist keeps its id where free, and takes a new one where not`() {
        val plan = planLibraryMerge(
            backup(listOf(playlist(3, "Road"), playlist(5, "Gym"))),
            local(listOf(localPlaylist(3, "Mine"))),
            trackIds::get,
        )

        assertEquals(mapOf(3L to 6L, 5L to 5L), plan.playlistIds)
    }

    @Test
    fun `a track this device lacks is left out`() {
        val plan = planLibraryMerge(backup(listOf(playlist(7, "Road", "/m/a.mp3", "/m/gone.mp3"))), local(), trackIds::get)

        assertEquals(listOf(1L), plan.playlistTracks.map { it.trackId })
    }

    @Test
    fun `a cover image the backup could not carry falls back to the playlist's tracks`() {
        val plan = planLibraryMerge(backup(listOf(playlist(7, "Road", coverSource = PlaylistCoverSource.IMAGE))), local(), trackIds::get)

        assertEquals(PlaylistCoverSource.STACKED, plan.newPlaylists.single().playlist.coverSource)
    }

    @Test
    fun `play counts take the higher, never the sum`() {
        val plan = planLibraryMerge(
            backup(playStats = listOf(BackedUpPlayStats("/m/a.mp3", playCount = 4, lastPlayedAt = 100L))),
            local(playStats = listOf(PlayStatsEntity(trackId = 1L, playCount = 9, lastPlayedAt = 50L))),
            trackIds::get,
        )

        assertEquals(listOf(PlayStatsEntity(1L, playCount = 9, lastPlayedAt = 100L)), plan.playStats)
    }

    @Test
    fun `favorites and orders follow their playlist to its id here`() {
        val plan = planLibraryMerge(
            backup(
                playlists = listOf(playlist(7, "Road")),
                favorites = listOf(BackedUpFavoriteCollection("playlist:7", favoritedAt = 1L), BackedUpFavoriteCollection("album:42", favoritedAt = 1L)),
                arrangements = listOf(BackedUpArrangement(SortableList.PLAYLIST_TRACKS, "7", "/m/b.mp3", position = 0)),
            ),
            local(listOf(localPlaylist(7, "Taken"), localPlaylist(3, "Road"))),
            trackIds::get,
        )

        assertEquals(listOf("playlist:3", "album:42"), plan.favoriteCollections.map { it.collection })
        assertEquals(listOf(ArrangementEntity(SortableList.PLAYLIST_TRACKS, "3", 2L, 0)), plan.arrangements)
    }

    @Test
    fun `merging what is already merged adds nothing new`() {
        val backup = backup(
            playlists = listOf(playlist(7, "Road", "/m/a.mp3")),
            playStats = listOf(BackedUpPlayStats("/m/a.mp3", playCount = 4, lastPlayedAt = 100L)),
        )
        val first = planLibraryMerge(backup, local(), trackIds::get)
        val afterFirst = local(
            playlists = first.newPlaylists.map { it.playlist },
            playStats = first.playStats,
        )

        val second = planLibraryMerge(backup, afterFirst, trackIds::get)

        assertEquals(emptyList<NewPlaylist>(), second.newPlaylists)
        assertEquals(first.playlistIds, second.playlistIds)
        assertEquals(first.playStats, second.playStats)
    }
}
