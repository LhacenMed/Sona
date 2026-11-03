package com.lhacenmed.sona.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NameKeyTest {

    @Test
    fun namesDifferingOnlyInCase_areOne() {
        assertEquals(nameKey("5 Seconds of Summer"), nameKey("5 Seconds Of Summer"))
    }

    @Test
    fun namesDifferingOnlyInInvisibleMarks_areOne() {
        assertEquals(nameKey("عبدالله دداه"), nameKey("\u200Fعبدالله\u200D دداه\u200E"))
        assertEquals(nameKey("Sidi"), nameKey("\uFEFFSidi\u200B"))
    }

    @Test
    fun namesDifferingOnlyInSpacing_areOne() {
        assertEquals(nameKey("Abd El Fattah Grini"), nameKey(" Abd  El\u00A0Fattah   Grini "))
    }

    @Test
    fun namesWrittenWithDifferentCodePoints_areOne() {
        assertEquals(nameKey("Beyonc\u00E9"), nameKey("Beyonce\u0301"))
        assertEquals(nameKey("سيدي"), nameKey("\uFEB3\uFEF4\uFEA9\uFEF1"))
    }

    @Test
    fun aKeyOfAnOrdinaryName_isItsLowercase_soTheIdsOfTheLibraryStayAsTheyAre() {
        assertEquals("abba", nameKey("ABBA"))
        assertEquals("عبدالله دداه", nameKey("عبدالله دداه"))
    }

    @Test
    fun differentNames_stayDifferent() {
        assertNotEquals(nameKey("1950 SoulClub"), nameKey("1960 SoulClub"))
        assertNotEquals(nameKey("Sidi"), nameKey("Sidi Mohamed"))
    }
}
