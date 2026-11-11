package com.lhacenmed.sona.core.downloader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptionsTest {

    private val wanted = listOf("fr", "en")

    /** YouTube's captions made from the speech in [spoken], with its translations into [into], as yt-dlp lists them. */
    private fun speech(spoken: String, vararg into: String) = listOf("$spoken-orig", spoken) + into

    @Test
    fun `captions made from the speech come once, and never one YouTube translated`() {
        assertEquals(listOf("en"), captionsToEmbed(emptyList(), speech("en", "fr", "de"), wanted))
    }

    @Test
    fun `captions made by people come first, regional where it has no other, then the speech's own`() {
        assertEquals(listOf("en"), captionsToEmbed(listOf("en"), speech("en", "fr", "fr-en", "en-en"), wanted))
        assertEquals(listOf("fr", "en"), captionsToEmbed(listOf("en"), speech("fr", "en", "fr-en"), wanted))
        assertEquals(listOf("en-GB"), captionsToEmbed(listOf("en-GB", "live_chat"), emptyList(), wanted))
    }

    @Test
    fun `with none in either language, the language spoken`() {
        assertEquals(listOf("ar"), captionsToEmbed(emptyList(), speech("ar", "en", "fr"), wanted))
        // Another site's captions made from the speech are listed once, as their language.
        assertEquals(listOf("yue"), captionsToEmbed(emptyList(), listOf("yue"), wanted))
        assertEquals(listOf("ar"), captionsToEmbed(listOf("live_chat", "ar"), emptyList(), wanted))
        assertEquals(emptyList<String>(), captionsToEmbed(listOf("live_chat"), emptyList(), wanted))
    }

    @Test
    fun `a language's old code is its new one`() {
        assertEquals(listOf("iw"), captionsToEmbed(listOf("iw"), emptyList(), listOf("he")))
        assertEquals("he", captionLanguagesOf(JSONObject("""{"subtitles":{"he":[]}}"""), "iw"))
    }

    @Test
    fun `yt-dlp is told the codes chosen, or nothing where there are none, and a playlist's videos the speech's`() {
        val read = JSONObject("""{"subtitles":{"en":[]},"automatic_captions":{"en-orig":[],"en":[],"fr":[],"fr-en":[]}}""")
        assertEquals("en", captionLanguagesOf(read, "fr"))
        assertNull(captionLanguagesOf(JSONObject("{}"), "fr"))
        assertEquals(".*-orig", captionLanguagesOf(null, "fr"))
    }

    @Test
    fun `a translation is made from people's captions in the language spoken, else the speech's`() {
        fun info(made: List<String>, automatic: List<String>) = MediaInfo(null, "T", null, null, null, null, emptyList(), null, null, null, null, null, made, automatic)
        assertEquals("en", info(listOf("pt-BR", "en"), speech("en", "fr")).captionGroups.translationSource)
        assertEquals("en", info(listOf("pt-BR"), speech("en", "fr")).captionGroups.translationSource)
        assertEquals("pt-BR", info(listOf("pt-BR"), emptyList()).captionGroups.translationSource)
        assertEquals(listOf("en"), info(emptyList(), speech("en", "fr")).captionGroups.spoken)
        assertEquals(translatedCaptionCode("fr", "en"), "fr@en")
        assertEquals("fr", captionLanguageOf("fr@en"))
    }

    @Test
    fun `captions are read from YouTube's sentences and from WebVTT, and written as SubRip`() {
        val sentences = sentencesOf("""<transcript><text start="1.36" dur="1.68">I&amp;#39;m here</text><text start="3.04" dur="2">now</text></transcript>""")
        assertEquals(listOf(CaptionLine(1360, 3040, "I'm here"), CaptionLine(3040, 5040, "now")), sentences)
        val vtt = vttLinesOf("WEBVTT\n\n00:00:01.000 --> 00:00:02.500 align:start\n<c>Hello</c> there\n\n01:02.000 --> 01:03.000\nAgain")
        assertEquals(listOf(CaptionLine(1000, 2500, "Hello there"), CaptionLine(62000, 63000, "Again")), vtt)
        assertEquals("1\n00:00:01,360 --> 00:00:03,040\nI'm here\n", srtOf(sentences.take(1)))
    }
}
