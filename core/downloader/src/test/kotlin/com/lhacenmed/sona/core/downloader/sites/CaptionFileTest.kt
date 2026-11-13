package com.lhacenmed.sona.core.downloader.sites

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionFileTest {
    @Test
    fun everySitesLanguageNameIsOneLanguageTag() {
        assertEquals("en-US", languageTagOf("en_US"))
        assertEquals("en-US", languageTagOf("eng-US"))
        assertEquals("es-ES", languageTagOf("spa-ES"))
        assertEquals("zh-Hans-CN", languageTagOf("cmn-Hans-CN"))
        assertEquals("pt-BR", languageTagOf("pt-BR"))
        assertEquals("und", languageTagOf(null))
        assertEquals("und", languageTagOf(" "))
    }
}
