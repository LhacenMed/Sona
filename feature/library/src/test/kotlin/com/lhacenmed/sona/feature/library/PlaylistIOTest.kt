package com.lhacenmed.sona.feature.library

import com.lhacenmed.sona.core.data.playlist.PlaylistFile
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistIOTest {

    private fun track(id: Long) = Track(
        id = id, mediaStoreId = id, title = "Title $id", artist = "Artist", artistId = 1, album = "Album",
        albumId = 1, genre = null, genreId = null, path = "/music/$id.mp3", folderPath = "/music",
        durationMs = 60_000, trackNumber = null, discNumber = null, year = null, dateAddedSeconds = 0,
        coverArtUri = null, isManuallyScanned = false, isVideo = false,
    )

    private val library = listOf(track(1), track(2), track(3))

    @Test
    fun aPlaylistComesBackInItsOrderWithItsSortAndDates() {
        val exported = PlaylistFile(
            entries = listOf(PlaylistFile.Entry(track(3), 300), PlaylistFile.Entry(track(1), 100), PlaylistFile.Entry(track(2), 200)),
            order = SortOrder(SortCriterion.CUSTOM, SortDirection.DESCENDING),
        )
        val bytes = ByteArrayOutputStream().also { writeM3u(it, exported) }.toByteArray()

        assertEquals(exported, readM3u(ByteArrayInputStream(bytes), library))
    }

    @Test
    fun anotherPlayersFileKeepsItsOrderAndSaysNothingMore() {
        val m3u = "#EXTM3U\n#EXTINF:60,Artist - Title 2\n/music/2.mp3\n/elsewhere/1.mp3\n/music/9.mp3\n"

        val imported = readM3u(ByteArrayInputStream(m3u.toByteArray()), library)

        assertEquals(listOf(2L, 1L), imported.entries.map { it.track.id })
        assertEquals(listOf(null, null), imported.entries.map { it.addedAt })
        assertNull(imported.order)
    }
}
