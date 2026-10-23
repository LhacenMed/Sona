package com.lhacenmed.sona.core.common.format

import org.junit.Assert.assertEquals
import org.junit.Test

class TagTextTest {

    @Test
    fun readsUtf8TakenForLatin1AsWritten() {
        assertEquals("Señorita - Single", "SeÃ±orita - Single".repairedTagText())
        assertEquals("Beyoncé", "BeyoncÃ©".repairedTagText())
    }

    @Test
    fun readsUtf8TakenForWindows1252AsWritten() {
        assertEquals("Don’t Start Now", "Donâ€™t Start Now".repairedTagText())
    }

    @Test
    fun readsUtf8OfOtherScriptsTakenForLatin1AsWritten() {
        val arabic = "اعل سالم"
        assertEquals(arabic, String(arabic.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1).repairedTagText())
    }

    @Test
    fun leavesTextReadRightAsItIs() {
        listOf("Señorita", "Beyoncé", "Mr. Feel", "اعل سالم", "Камин", "Ça va", "Æther").forEach { text ->
            assertEquals(text, text.repairedTagText())
        }
    }
}
