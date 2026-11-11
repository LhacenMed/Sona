package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.model.AudioFormat
import com.lhacenmed.sona.core.model.ClipRange
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.core.model.SponsorSegment
import com.lhacenmed.sona.core.model.VideoFormat
import com.lhacenmed.sona.core.model.VideoQuality
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpArgumentsTest {

    private fun List<String>.valueOf(option: String): String? = getOrNull(indexOf(option) + 1)?.takeIf { option in this }

    private fun DownloadChoices.arguments(
        language: String = "en",
        options: DownloadOptions = DownloadOptions(),
        preferences: DownloaderPreferences = DownloaderPreferences(),
        decodesAv1InHardware: Boolean = false,
    ) = toArguments(options, preferences, language, decodesAv1InHardware)

    @Test
    fun `a video's format prefers H264 for sharing, or AV1 where decoded in hardware and VP9 otherwise for quality`() {
        val legacy = DownloadChoices(kind = DownloadKind.VIDEO, videoFormat = VideoFormat.LEGACY).arguments()
        assertEquals("proto,vcodec:h264,ext,res:1080", legacy.valueOf("-S"))

        val quality = DownloadChoices(kind = DownloadKind.VIDEO, videoFormat = VideoFormat.QUALITY)
        assertEquals("vcodec:vp9.2,res:1080", quality.arguments().valueOf("-S"))
        assertEquals("vcodec:av01,res:1080", quality.arguments(decodesAv1InHardware = true).valueOf("-S"))
        assertEquals("bv*+ba/b", quality.arguments().valueOf("-f"))
    }

    @Test
    fun `a video is kept at its quality, as mp4, with its captions and chapters`() {
        val arguments = DownloadChoices(kind = DownloadKind.VIDEO, videoQuality = VideoQuality.P720, captions = true).arguments("fr")
        assertEquals("vcodec:vp9.2,res:720", arguments.valueOf("-S"))
        assertEquals("mp4", arguments.valueOf("--merge-output-format"))
        assertEquals("fr.*,en.*,-live_chat", arguments.valueOf("--sub-langs"))
        assertEquals("srt", arguments.valueOf("--convert-subs"))
        assertTrue("--embed-subs" in arguments)
        assertTrue("--embed-chapters" in arguments)
        assertFalse("-x" in arguments)
    }

    @Test
    fun `the best video has no limit, and no captions unless asked`() {
        val arguments = DownloadChoices(kind = DownloadKind.VIDEO, videoQuality = VideoQuality.BEST, captions = false).arguments()
        assertEquals("vcodec:vp9.2", arguments.valueOf("-S"))
        assertFalse("--write-subs" in arguments)
    }

    @Test
    fun `a format picked replaces what the quality would pick`() {
        val arguments = DownloadChoices(kind = DownloadKind.VIDEO, videoQuality = VideoQuality.P720).arguments(options = DownloadOptions(formatId = "137+ba/137"))
        assertEquals("137+ba/137", arguments.valueOf("-f"))
        assertNull(arguments.valueOf("-S"))
    }

    @Test
    fun `audio is extracted with its cover, converted only when asked`() {
        val original = DownloadChoices(kind = DownloadKind.AUDIO, audioFormat = AudioFormat.ORIGINAL).arguments()
        assertTrue("-x" in original)
        assertTrue("--embed-thumbnail" in original)
        assertNull(original.valueOf("--audio-format"))
        assertFalse("--write-subs" in original)

        val mp3 = DownloadChoices(kind = DownloadKind.AUDIO, audioFormat = AudioFormat.MP3).arguments()
        assertEquals("mp3", mp3.valueOf("--audio-format"))
    }

    @Test
    fun `sponsors are cut only when asked, the parts the settings name`() {
        assertFalse("--sponsorblock-remove" in DownloadChoices(skipSponsors = false).arguments())
        val preferences = DownloaderPreferences(sponsorSegments = setOf(SponsorSegment.SPONSOR, SponsorSegment.INTRO))
        assertEquals("sponsor,intro", DownloadChoices(skipSponsors = true).arguments(preferences = preferences).valueOf("--sponsorblock-remove"))
    }

    @Test
    fun `a clip downloads its stretch alone, and chapters split into files`() {
        val arguments = DownloadChoices().arguments(options = DownloadOptions(clip = ClipRange(90, 165), splitChapters = true))
        assertEquals("*90-165", arguments.valueOf("--download-sections"))
        assertTrue("--split-chapters" in arguments)
    }

    @Test
    fun `an item line names the video, and where it stands in a playlist`() {
        assertEquals(DownloadEvent.Item("Song title", 2, 5), parseLine("${YtDlpLines.ITEM} 2 5 Song title"))
        assertEquals(DownloadEvent.Item("One video", null, null), parseLine("${YtDlpLines.ITEM}   One video"))
    }

    @Test
    fun `a progress line is how far and how fast, nothing known where yt-dlp says NA`() {
        val progress = parseLine("${YtDlpLines.PROGRESS} 250 1000 50.5") as DownloadEvent.Progress
        assertEquals(0.25f, progress.fraction)
        assertEquals(50L, progress.bytesPerSecond)
        assertNull((parseLine("${YtDlpLines.PROGRESS} 250 NA NA") as DownloadEvent.Progress).fraction)
    }

    @Test
    fun `a chapter's file is saved too`() {
        assertEquals(
            DownloadEvent.Saved(File("/sdcard/Download/Sona/Video/01 - Intro.mp4"), null, null),
            parseLine("[SplitChapters] Chapter 001; Destination: /sdcard/Download/Sona/Video/01 - Intro.mp4"),
        )
    }

    @Test
    fun `anything else yt-dlp writes says nothing`() {
        assertNull(parseLine("[download] Destination: video.mp4"))
    }

    @Test
    fun `arguments split at spaces, outside quotes`() {
        assertEquals(listOf("--embed-subs", "-o", "%(title)s [%(id)s].%(ext)s", "x"), splitArguments("""--embed-subs -o "%(title)s [%(id)s].%(ext)s" 'x'"""))
        assertEquals(emptyList<String>(), splitArguments("   "))
    }

    @Test
    fun `failures are told apart by what they say`() {
        assertEquals(DownloadFailure.LOGIN_REQUIRED, failureOf(YtDlpException("Sign in to confirm you're not a bot")))
        assertEquals(DownloadFailure.UNAVAILABLE, failureOf(YtDlpException("[youtube] abc: Video unavailable")))
        assertEquals(DownloadFailure.NO_DOWNLOADER, failureOf(DownloaderMissingException()))
        assertEquals(DownloadFailure.FAILED, failureOf(YtDlpException("Something else")))
        assertTrue(isStaleRead(YtDlpException("HTTP Error 403: Forbidden")))
    }

    @Test
    fun `a song is downloaded straight from its site only where nothing is to be assembled or converted`() {
        val m4a = MediaFormat("140", "m4a", null, null, null, "mp4a.40.2", 129, 4_000_000, false, "https://example.com/a", emptyMap())
        val video = MediaFormat("137", "mp4", 1080, 30, "avc1", null, 4000, null, true, "https://example.com/v", emptyMap())
        val info = MediaInfo("id", "Song", "Artist", 200, null, "Youtube", listOf(m4a, video), null, null, null, null, null, hasCaptions = false)
        val transfer = DirectTransfer()
        val audio = DownloadChoices(kind = DownloadKind.AUDIO, audioFormat = AudioFormat.ORIGINAL)
        assertEquals(m4a, transfer.formatFor(info, audio, DownloadOptions()))
        assertNull(transfer.formatFor(info, audio.copy(audioFormat = AudioFormat.MP3), DownloadOptions()))
        assertNull(transfer.formatFor(info, audio.copy(skipSponsors = true), DownloadOptions()))
        assertNull(transfer.formatFor(info, audio, DownloadOptions(clip = ClipRange(0, 10))))
        assertNull(transfer.formatFor(info, DownloadChoices(kind = DownloadKind.VIDEO), DownloadOptions()))
    }

    @Test
    fun `a video is downloaded straight from its site only where one whole file is the best there is`() {
        fun mp4(id: String, height: Int, hasAudio: Boolean) =
            MediaFormat(id, "mp4", height, 30, "avc1", if (hasAudio) "mp4a" else null, height * 2, null, true, "https://example.com/$id", emptyMap())
        val transfer = DirectTransfer()
        val choices = DownloadChoices(kind = DownloadKind.VIDEO, videoQuality = VideoQuality.P1080)
        // A post's video: every variant whole, with its sound - the tallest within the quality is taken.
        val post = MediaInfo("p", "Post", null, 30, null, "Twitter", listOf(mp4("480", 480, true), mp4("720", 720, true), mp4("1440", 1440, true)), null, null, null, null, null, hasCaptions = false)
        assertEquals("720", transfer.formatFor(post, choices, DownloadOptions())?.id)
        // YouTube's: its whole file is the smallest, the rest pictures to merge - yt-dlp's to download.
        val youTube = post.copy(formats = listOf(mp4("18", 360, true), mp4("137", 1080, false)))
        assertNull(transfer.formatFor(youTube, choices, DownloadOptions()))
        assertNull(transfer.formatFor(post.copy(hasCaptions = true), choices, DownloadOptions()))
    }
}
