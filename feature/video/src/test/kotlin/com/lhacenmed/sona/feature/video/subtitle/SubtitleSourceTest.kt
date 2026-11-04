package com.lhacenmed.sona.feature.video.subtitle

import com.lhacenmed.sona.feature.playback.MediaTrack
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSourceTest {

    private val attached = SubtitleFile(File("7.srt"), "Film.srt", null, isAttached = true, modifiedAt = 0L)
    private val beside = SubtitleFile(File("Film.srt"), "Film.srt", null, isAttached = false, modifiedAt = 0L)
    private val besideArabic = SubtitleFile(File("Film.ar.srt"), "Film.ar.srt", "ar", isAttached = false, modifiedAt = 0L)
    private val english = track(trackIndex = 0, language = "en")
    private val arabic = track(trackIndex = 1, language = "ara")
    private val forcedArabic = track(trackIndex = 2, language = "ar", isForced = true)

    private fun track(trackIndex: Int, language: String?, isForced: Boolean = false) =
        MediaTrack(groupIndex = 3, trackIndex = trackIndex, language = language, label = null, channelCount = 0, isForced = isForced, isSelected = false)

    private fun choose(
        choice: String? = null,
        attached: SubtitleFile? = null,
        sideFiles: List<SubtitleFile> = emptyList(),
        tracks: List<MediaTrack> = emptyList(),
        isOn: Boolean = true,
        language: String? = null,
    ) = chooseSubtitleSource(choice, attached, sideFiles, tracks, isOn, language)

    @Test
    fun `what the user picked for a video holds whatever else there is`() {
        assertEquals(SubtitleSource.Off, choose(SubtitleSource.Off.storedChoice, attached, listOf(beside), isOn = true))
        val picked = SubtitleSource.Embedded(english)
        assertEquals(picked, choose(picked.storedChoice, attached, listOf(beside), listOf(english), isOn = false))
        val pickedFile = SubtitleSource.FromFile(besideArabic)
        assertEquals(pickedFile, choose(pickedFile.storedChoice, attached, listOf(beside, besideArabic), isOn = false))
    }

    @Test
    fun `a pick no longer there is decided again`() {
        val gone = SubtitleSource.FromFile(besideArabic).storedChoice

        assertEquals(SubtitleSource.FromFile(beside), choose(gone, sideFiles = listOf(beside)))
        assertEquals(SubtitleSource.Off, choose(gone, sideFiles = listOf(beside), isOn = false))
    }

    @Test
    fun `subtitles turned off stay off for the next video`() {
        assertEquals(SubtitleSource.Off, choose(attached = attached, sideFiles = listOf(beside), tracks = listOf(english), isOn = false))
    }

    @Test
    fun `an attached file comes first, then a file beside the video in the language last chosen`() {
        assertEquals(SubtitleSource.FromFile(attached), choose(attached = attached, sideFiles = listOf(besideArabic)))
        assertEquals(SubtitleSource.FromFile(besideArabic), choose(sideFiles = listOf(beside, besideArabic), language = "ar"))
        assertEquals(SubtitleSource.FromFile(beside), choose(sideFiles = listOf(beside, besideArabic), language = "fr"))
    }

    @Test
    fun `the video's own track shows only in the language last chosen, and never a forced one for it`() {
        assertEquals(SubtitleSource.Embedded(arabic), choose(tracks = listOf(english, forcedArabic, arabic), language = "ar"))
        assertEquals(SubtitleSource.PlayerDefault, choose(tracks = listOf(english, arabic)))
        assertEquals(SubtitleSource.PlayerDefault, choose(tracks = listOf(forcedArabic), language = "ar"))
    }

    @Test
    fun `languages match whichever form each code is in`() {
        assertTrue(isSameLanguage("ar", "ara"))
        assertTrue(isSameLanguage("ar-EG", "ar"))
        assertTrue(isSameLanguage("EN", "eng"))
        assertFalse(isSameLanguage("ar", "en"))
        assertFalse(isSameLanguage(null, "en"))
    }
}
