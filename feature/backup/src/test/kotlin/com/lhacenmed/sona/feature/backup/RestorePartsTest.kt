package com.lhacenmed.sona.feature.backup

import com.lhacenmed.sona.core.database.entity.PlaylistCoverSource
import com.lhacenmed.sona.core.model.FAVORITES_PLAYLIST_ID
import com.lhacenmed.sona.core.model.sort.SortableList
import org.junit.Assert.assertEquals
import org.junit.Test

class RestorePartsTest {

    private fun playlist(id: Long) = BackedUpPlaylist(
        id = id,
        name = "p$id",
        isBuiltIn = id == FAVORITES_PLAYLIST_ID,
        createdAt = 0L,
        modifiedAt = 0L,
        coverSource = PlaylistCoverSource.STACKED,
        coverTrackPath = null,
        coverImage = null,
        tracks = listOf(BackedUpPlaylistTrack("/m/a.mp3", 0L)),
    )

    private val library = BackupLibrary(
        playlists = listOf(playlist(FAVORITES_PLAYLIST_ID), playlist(7)),
        favoriteCollections = listOf(BackedUpFavoriteCollection("album:1", 0L)),
        arrangements = listOf(
            BackedUpArrangement(SortableList.PLAYLIST_TRACKS, "7", "/m/a.mp3", 0),
            BackedUpArrangement(SortableList.PLAYLIST_TRACKS, FAVORITES_PLAYLIST_ID.toString(), "/m/a.mp3", 0),
        ),
        playStats = listOf(BackedUpPlayStats("/m/a.mp3", 3, 0L)),
        resumePositions = emptyList(),
        media = emptyList(),
    )

    @Test
    fun everyPartKeepsTheWholeLibrary() {
        assertEquals(library, library.keeping(RestorePart.entries.toSet()))
    }

    @Test
    fun playlistsLeftOutTakeTheirOrderButLeaveFavorites() {
        val kept = library.keeping(setOf(RestorePart.FAVORITES, RestorePart.HISTORY))
        assertEquals(listOf(FAVORITES_PLAYLIST_ID), kept.playlists.map { it.id })
        assertEquals(listOf(FAVORITES_PLAYLIST_ID.toString()), kept.arrangements.map { it.instanceId })
        assertEquals(1, kept.favoriteCollections.size)
        assertEquals(1, kept.playStats.size)
    }

    @Test
    fun favoritesAndHistoryLeftOut() {
        val kept = library.keeping(setOf(RestorePart.PLAYLISTS))
        assertEquals(listOf(7L), kept.playlists.map { it.id })
        assertEquals(emptyList<BackedUpFavoriteCollection>(), kept.favoriteCollections)
        assertEquals(emptyList<BackedUpPlayStats>(), kept.playStats)
    }
}
