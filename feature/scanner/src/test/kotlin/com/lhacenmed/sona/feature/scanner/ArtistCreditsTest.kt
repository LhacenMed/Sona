package com.lhacenmed.sona.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistCreditsTest {

    private val credits = listOf(
        "Justin Bieber, Benny Blanco & Billie Eilish",
        "Billie Eilish",
        "Conor Maynard × The Vamps",
        "Earth, Wind & Fire",
        "Simon & Garfunkel",
        "AC/DC",
        "Tyler, The Creator",
        "Drake feat. Rihanna",
        "Lil Nas X",
    )

    private val separated = ArtistCredits(credits, separatesArtists = true)

    @Test
    fun partsACreditAtACommaOrAmpersandWhereOneOfItsArtistsIsCreditedAlone() {
        assertEquals(listOf("Justin Bieber", "Benny Blanco", "Billie Eilish"), separated.artistsOf("Justin Bieber, Benny Blanco & Billie Eilish"))
    }

    @Test
    fun alwaysPartsACreditAtASeparatorOnlyFoundBetweenArtists() {
        assertEquals(listOf("Conor Maynard", "The Vamps"), separated.artistsOf("Conor Maynard × The Vamps"))
        assertEquals(listOf("Drake", "Rihanna"), separated.artistsOf("Drake feat. Rihanna"))
        assertEquals(listOf("Calvin Harris", "Dua Lipa"), separated.artistsOf("Calvin Harris x Dua Lipa"))
        assertEquals(listOf("Artist", "Guest"), separated.artistsOf("Artist (feat. Guest)"))
    }

    @Test
    fun keepsANameThatOnlyLooksLikeSeveral() {
        listOf("Earth, Wind & Fire", "Simon & Garfunkel", "AC/DC", "Tyler, The Creator", "Lil Nas X").forEach { name ->
            assertEquals(listOf(name), separated.artistsOf(name))
        }
    }

    @Test
    fun keepsEveryCreditWholeWhileArtistsAreNotSeparated() {
        val credit = "Justin Bieber, Benny Blanco & Billie Eilish"
        assertEquals(listOf(credit), ArtistCredits(credits, separatesArtists = false).artistsOf(credit))
    }
}
