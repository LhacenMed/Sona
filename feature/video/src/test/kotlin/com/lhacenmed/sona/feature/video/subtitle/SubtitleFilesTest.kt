package com.lhacenmed.sona.feature.video.subtitle

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleFilesTest {

    private val arabicLine = "مرحبا"

    @Test
    fun `a video's subtitles are those of its own name, with or without a language`() {
        val found = sideSubtitlesOf(
            "Film (2020).mkv",
            listOf("Film (2020).srt", "film (2020).AR.ass", "Film (2020).en.forced.vtt", "Film (2020).mkv", "Film (2020) extras.srt", "Other.srt", "Film (2020).nfo"),
        )

        assertEquals(
            listOf(
                SideSubtitle("film (2020).AR.ass", "AR"),
                SideSubtitle("Film (2020).en.forced.vtt", "en"),
                SideSubtitle("Film (2020).srt", null),
            ),
            found,
        )
    }

    @Test
    fun `episodes do not take each other's subtitles`() {
        val siblings = listOf("Show.S01E01.srt", "Show.S01E02.srt", "Show.S01E10.srt")

        assertEquals(listOf(SideSubtitle("Show.S01E01.srt", null)), sideSubtitlesOf("Show.S01E01.mkv", siblings))
    }

    @Test
    fun `a name after the video's that is no language code says none`() {
        assertEquals(listOf(SideSubtitle("Film.Director's Cut.srt", null)), sideSubtitlesOf("Film.mkv", listOf("Film.Director's Cut.srt")))
    }

    @Test
    fun `UTF-8 is kept as it is, without its marker`() {
        val utf8 = arabicLine.toByteArray(Charsets.UTF_8)
        val noDetection: (ByteArray) -> String? = { error("Valid UTF-8 needs no detecting") }

        assertArrayEquals(utf8, subtitleBytesAsUtf8(utf8, noDetection))
        assertArrayEquals(utf8, subtitleBytesAsUtf8(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + utf8, noDetection))
    }

    @Test
    fun `UTF-16 is told by its marker`() {
        val noDetection: (ByteArray) -> String? = { error("A marker needs no detecting") }
        val littleEndian = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + arabicLine.toByteArray(Charsets.UTF_16LE)
        val bigEndian = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + arabicLine.toByteArray(Charsets.UTF_16BE)

        assertEquals(arabicLine, subtitleBytesAsUtf8(littleEndian, noDetection).decodeToString())
        assertEquals(arabicLine, subtitleBytesAsUtf8(bigEndian, noDetection).decodeToString())
    }

    @Test
    fun `a regional encoding is turned into UTF-8 as detected`() {
        val windows1256 = arabicLine.toByteArray(Charset.forName("windows-1256"))

        assertEquals(arabicLine, subtitleBytesAsUtf8(windows1256) { "windows-1256" }.decodeToString())
    }

    @Test
    fun `an encoding not detected or not known falls back rather than fails`() {
        val bytes = byteArrayOf(0xE9.toByte())

        assertEquals("é", subtitleBytesAsUtf8(bytes) { null }.decodeToString())
        assertEquals("é", subtitleBytesAsUtf8(bytes) { "no-such-charset" }.decodeToString())
    }

    @Test
    fun `each regional alphabet is told from its bytes, whatever the device's language`() {
        val lines = mapOf(
            "windows-1256" to "\u0645\u0631\u062D\u0628\u0627 \u0628\u0627\u0644\u0639\u0627\u0644\u0645\u060C \u0643\u064A\u0641 \u062D\u0627\u0644\u0643 \u0627\u0644\u064A\u0648\u0645\u061F",
            "windows-1251" to "\u041F\u0440\u0438\u0432\u0435\u0442, \u043A\u0430\u043A \u0442\u0432\u043E\u0438 \u0434\u0435\u043B\u0430 \u0441\u0435\u0433\u043E\u0434\u043D\u044F?",
            "windows-1255" to "\u05E9\u05DC\u05D5\u05DD \u05E2\u05D5\u05DC\u05DD, \u05DE\u05D4 \u05E9\u05DC\u05D5\u05DE\u05DA \u05D4\u05D9\u05D5\u05DD?",
            "windows-1253" to "\u0393\u03B5\u03B9\u03B1 \u03C3\u03BF\u03C5 \u03BA\u03CC\u03C3\u03BC\u03B5, \u03C0\u03CE\u03C2 \u03B5\u03AF\u03C3\u03B1\u03B9 \u03C3\u03AE\u03BC\u03B5\u03C1\u03B1;",
        )
        for ((encoding, line) in lines) {
            val bytes = "1\n00:00:01,000 --> 00:00:02,000\n$line\n".toByteArray(Charset.forName(encoding))

            assertEquals(line, encoding, detectSubtitleCharset(bytes, deviceLanguage = "en"))
            assertEquals(line, encoding, detectSubtitleCharset(bytes, deviceLanguage = "fr"))
        }
    }

    @Test
    fun `accented Latin text is not taken for another alphabet, whatever the device's language`() {
        val french = "J'ai \u00E9t\u00E9 \u00E0 la for\u00EAt, c'\u00E9tait tr\u00E8s beau.".toByteArray(Charset.forName("windows-1252"))

        assertEquals("windows-1252", detectSubtitleCharset(french, deviceLanguage = "ar"))
        assertEquals("windows-1252", detectSubtitleCharset(french, deviceLanguage = "fr"))
    }

    @Test
    fun `a Latin encoding of its own follows the device's language`() {
        val turkish = "G\u00FCnayd\u0131n, nas\u0131ls\u0131n bug\u00FCn?".toByteArray(Charset.forName("windows-1254"))

        assertEquals("windows-1254", detectSubtitleCharset(turkish, deviceLanguage = "tr"))
    }

    @Test
    fun `an East Asian encoding, which bytes alone cannot tell, follows the device's language`() {
        val japanese = "\u3053\u3093\u306B\u3061\u306F\u4E16\u754C".toByteArray(Charset.forName("Shift_JIS"))

        assertEquals("Shift_JIS", detectSubtitleCharset(japanese, deviceLanguage = "ja"))
    }

    @Test
    fun `a stream far larger than any subtitle file is refused`() {
        assertNull(ByteArrayInputStream(ByteArray(9 * 1024 * 1024)).readSubtitleBytes())
        assertEquals(3, ByteArrayInputStream(ByteArray(3)).readSubtitleBytes()?.size)
    }
}
