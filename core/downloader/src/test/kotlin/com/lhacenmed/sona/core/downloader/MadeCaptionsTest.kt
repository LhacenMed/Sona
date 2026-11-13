package com.lhacenmed.sona.core.downloader

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MadeCaptionsTest {
    private val lines = listOf(CaptionLine(1_000, 2_500, "Hello"))

    @Test
    fun captionsSonaMakesAreListedAsAFileUnderTheirLanguage() {
        val folder = Files.createTempDirectory("made").toFile()
        val read = JSONObject().put("id", "abc")
        assertEquals("fr", read.listMadeCaptions("fr", lines, folder))
        val file = read.getJSONObject("subtitles").getJSONArray("fr").getJSONObject(0)
        assertEquals("srt", file.getString("ext"))
        assertTrue(file.getString("url").startsWith("file:"))
        assertEquals("1\n00:00:01,000 --> 00:00:02,500\nHello\n", folder.resolve("abc.fr.srt").readText())
    }

    @Test
    fun aLanguageTheVideoHasAlreadyIsNeverWrittenOver() {
        val folder = Files.createTempDirectory("made").toFile()
        val read = JSONObject().put("id", "abc").put("automatic_captions", JSONObject().put("en", "own"))
        assertEquals("en-sona", read.listMadeCaptions("en", lines, folder))
        assertEquals("own", read.getJSONObject("automatic_captions").getString("en"))
    }
}
