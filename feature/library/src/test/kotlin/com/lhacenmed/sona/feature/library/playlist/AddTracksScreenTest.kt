package com.lhacenmed.sona.feature.library.playlist

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/** A screen reaches its activity serialized, as navigating to it serializes it - so what it carries must be, too. */
class AddTracksScreenTest {

    @Test
    fun `serializes for every destination`() {
        listOf(
            AddTracksScreen(PickDestination.Playlist(7L)),
            AddTracksScreen(PickDestination.PrivateFolder, startsOnVideos = true),
        ).forEach { screen -> assertEquals(screen, roundTrip(screen)) }
    }

    private fun roundTrip(screen: AddTracksScreen): Any {
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(screen) } }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
    }
}
