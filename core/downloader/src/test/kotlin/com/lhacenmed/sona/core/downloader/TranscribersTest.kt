package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.core.model.TranscriptionService.GROQ
import com.lhacenmed.sona.core.model.TranscriptionService.SUPADATA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TranscribersTest {
    @Test
    fun theChosenServiceGoesFirstAndOnlyThoseWithAKeyAreTried() {
        val both = DownloaderPreferences(supadataKey = "s", groqKey = "g")
        assertEquals(listOf(SUPADATA, GROQ), both.transcribers)
        assertEquals(listOf(GROQ, SUPADATA), both.copy(transcriptionService = GROQ).transcribers)
        assertEquals(listOf(SUPADATA), DownloaderPreferences(supadataKey = "s", transcriptionService = GROQ).transcribers)
        assertFalse(DownloaderPreferences(supadataKey = " ").transcribes)
    }
}
