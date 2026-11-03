package com.lhacenmed.sona.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistCreditsTest {

    private val separated = ArtistCredits(separatesArtists = true)

    @Test
    fun partsAListOfArtists_whateverElseTheLibraryHolds() {
        assertEquals(listOf("Sidi", "Loubabe", "Ediye"), separated.artistsOf("Sidi, Loubabe & Ediye"))
        assertEquals(listOf("Justin Bieber", "Benny Blanco", "Billie Eilish"), separated.artistsOf("Justin Bieber, Benny Blanco & Billie Eilish"))
        assertEquals(listOf("A", "B"), separated.artistsOf("A & B"))
        assertEquals(listOf("A", "B"), separated.artistsOf("A + B"))
        assertEquals(listOf("A", "B"), separated.artistsOf("A/B"))
    }

    @Test
    fun partsAtAnArabicComma() {
        assertEquals(listOf("سيدي", "لبابة"), separated.artistsOf("سيدي، لبابة"))
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
        listOf(
            "Earth, Wind & Fire",
            "Simon & Garfunkel",
            "AC/DC",
            "Tyler, The Creator",
            "Florence + The Machine",
            "Kool & The Gang",
            "Lil Nas X",
        ).forEach { name -> assertEquals(listOf(name), separated.artistsOf(name)) }
    }

    @Test
    fun keepsANameWholeAmongJoinedArtists() {
        assertEquals(listOf("Drake", "Earth, Wind & Fire"), separated.artistsOf("Drake feat. Earth, Wind & Fire"))
    }

    @Test
    fun keepsEveryCreditWholeWhileArtistsAreNotSeparated() {
        val credit = "Justin Bieber, Benny Blanco & Billie Eilish"
        assertEquals(listOf(credit), ArtistCredits(separatesArtists = false).artistsOf(credit))
    }
}
