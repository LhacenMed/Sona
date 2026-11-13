package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.core.model.VideoFormat

/** How yt-dlp is asked to say what it does - one line each, Sona's own, apart from everything else it writes. */
internal object YtDlpLines {
    const val ITEM = "SONA_ITEM"
    const val PROGRESS = "SONA_PROGRESS"
    const val SAVED = "SONA_SAVED"

    /** What a download's files come to, as yt-dlp expects: its sound's and its picture's, where they are fetched apart, then the whole's. */
    const val SIZE = "SONA_SIZE"
}

/** A music cover cut to a square from the middle of a video's wide frame. */
private const val SquareCover = "ThumbnailsConvertor+ffmpeg_o:-c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\""

/**
 * What yt-dlp is told to download with these choices, [options] and [preferences] - the one place each becomes yt-dlp's, so a
 * choice added is a line added here. [captionLanguages] is which of a video's captions are embedded - see
 * [captionLanguagesOf] - null for none. [decodesAv1InHardware] is whether the device decodes AV1 in hardware at the quality
 * chosen, which a video's format asks.
 */
internal fun DownloadChoices.toArguments(
    options: DownloadOptions,
    preferences: DownloaderPreferences,
    captionLanguages: String?,
    decodesAv1InHardware: Boolean,
): List<String> = buildList {
    when (kind) {
        DownloadKind.VIDEO -> {
            val formatId = options.formatId
            if (formatId != null) {
                addAll(listOf("-f", formatId))
            } else {
                // The sound first: small, it is soon whole, so the picture plays with it as it downloads - see LiveDownloads. With a
                // picture alone: one that may carry its own sound - a file whose codecs its site does not say, as Facebook's - is
                // merged away into the sound alone. Without one, yt-dlp's own order: the best picture with the best sound, else
                // the best whole file.
                val sound = audioLanguage?.let { "ba[language=$it]+bv/" }.orEmpty()
                addAll(listOf("-f", "${sound}ba+bv/bv*+ba/b", "-S", videoSorter(decodesAv1InHardware)))
            }
            addAll(listOf("--merge-output-format", "mp4"))
            captionLanguages?.let { languages ->
                addAll(captionArguments(languages))
                // In the video itself, as tracks of its own, which every player reads - and then only there: yt-dlp keeps the
                // files it embedded beside the video where it is told to write them, unless told otherwise.
                if (!subtitlesAsFile) addAll(listOf("--embed-subs", "--compat-options", "no-keep-subs"))
            }
        }

        DownloadKind.AUDIO -> {
            addAll(listOf("-f", options.formatId ?: inLanguage("ba", "ba/b"), "-x", "--audio-quality", "0"))
            audioFormat.extension?.let { addAll(listOf("--audio-format", it)) }
            addAll(listOf("--embed-thumbnail", "--convert-thumbnails", "jpg", "--ppa", SquareCover))
            // A song's own title and artist where the site knows them - YouTube Music's - the video's and its channel's otherwise.
            addAll(listOf("--parse-metadata", "%(track,title)s:%(meta_title)s", "--parse-metadata", "%(artist,uploader)s:%(meta_artist)s"))
        }

        // The video read, never fetched: its captions written alone - and nothing else here is for them.
        DownloadKind.SUBTITLES -> {
            add("--skip-download")
            captionLanguages?.let { addAll(captionArguments(it)) }
            return@buildList
        }
    }
    addAll(listOf("--embed-metadata", "--embed-chapters", "--parse-metadata", "%(release_year,upload_date)s:%(meta_date)s"))
    if (skipSponsors && preferences.sponsorSegments.isNotEmpty()) {
        addAll(listOf("--sponsorblock-remove", preferences.sponsorSegments.joinToString(",") { it.id }))
    }
    options.clip?.let { addAll(listOf("--download-sections", "*${it.startSeconds}-${it.endSeconds}", "--force-keyframes-at-cuts")) }
    if (options.splitChapters) add("--split-chapters")
}

/** What yt-dlp is told to write of a video's captions - those [languages] picks, made by people or by its site - as SubRip. */
private fun captionArguments(languages: String) = listOf("--write-subs", "--write-auto-subs", "--sub-langs", languages, "--convert-subs", "srt")

/**
 * [selector], its sound in [DownloadChoices.audioLanguage] where the video has its sound in it, else [otherwise] - which takes,
 * as yt-dlp ranks sounds by their `language_preference`, the one recorded.
 */
private fun DownloadChoices.inLanguage(selector: String, otherwise: String): String =
    audioLanguage?.let { "$selector[language=$it]/$otherwise" } ?: otherwise

/**
 * How yt-dlp ranks a video's formats - Seal's sorter: the codec its [VideoFormat] prefers, then no taller than the quality
 * chosen. Quality prefers AV1 only where the device decodes it in hardware, and VP9 otherwise.
 */
private fun DownloadChoices.videoSorter(decodesAv1InHardware: Boolean): String {
    val format = when (videoFormat) {
        VideoFormat.LEGACY -> "proto,vcodec:h264,ext"
        VideoFormat.QUALITY -> if (decodesAv1InHardware) "vcodec:av01" else "vcodec:vp9.2"
    }
    return listOfNotNull(format, videoQuality.height?.let { "res:$it" }).joinToString(",")
}

/**
 * What every request yt-dlp makes is told: through the proxy, at the rate, signed in and - where it [archives] - remembering what
 * it downloaded.
 */
internal fun DownloaderPreferences.networkArguments(runtime: DownloaderRuntime, archives: Boolean = true): List<String> = buildList {
    proxy.takeIf(String::isNotBlank)?.let { addAll(listOf("--proxy", it.trim())) }
    rateLimit.takeIf(String::isNotBlank)?.let { addAll(listOf("--limit-rate", it.trim())) }
    if (usesCookies && runtime.cookiesFile.exists()) addAll(listOf("--cookies", runtime.cookiesFile.path))
    if (usesArchive && archives) addAll(listOf("--download-archive", runtime.archiveFile.path))
}

/**
 * What yt-dlp is told about every download, whatever was chosen: to save to [outputDir] by way of [tempDir], named by
 * [fileNameTemplate] - a chapter's file within a folder of its video's, where it [splitsChapters] - and to say each item, its
 * progress - with yt-dlp's own words for it - and each file saved - with its Most replayed graph and its chapters - on lines
 * of Sona's own, beside all else it says.
 */
internal fun downloadArguments(outputDir: String, tempDir: String, fileNameTemplate: String, splitsChapters: Boolean): List<String> = buildList {
    addAll(listOf("-P", "home:$outputDir", "-P", "temp:$tempDir", "-o", fileNameTemplate))
    if (splitsChapters) addAll(listOf("-o", "chapter:%(title).100B/%(section_number)02d - %(section_title).100B.%(ext)s"))
    addAll(
        listOf(
            // A link to one video in a playlist is the video: a playlist's own link is still the playlist.
            "--no-playlist",
            // A stream in fragments - HLS, DASH - fetched eight at a time, as Seal fetches them.
            "--concurrent-fragments", "8",
            "--no-mtime",
            // Everything it does written out, as Seal and YTDLnis show it - --print alone would quiet it.
            "--no-quiet",
            "--progress",
            // Once a second at most: what the notification can show, and the log keeps.
            "--progress-delta", "1",
            "--progress-template",
            "download:${YtDlpLines.PROGRESS} %(progress.downloaded_bytes)s %(progress.total_bytes,progress.total_bytes_estimate)s %(progress.speed)s " +
                "%(progress._default_template)s",
            "--print", "before_dl:${YtDlpLines.ITEM} %(playlist_index|)s %(n_entries|)s %(title)s",
            "--print",
            "before_dl:${YtDlpLines.SIZE} %(requested_formats.0.filesize,requested_formats.0.filesize_approx|)s " +
                "%(requested_formats.1.filesize,requested_formats.1.filesize_approx|)s %(filesize,filesize_approx|)s",
            "--print", "after_move:${YtDlpLines.SAVED} %(.{filepath,heatmap,chapters})j",
        ),
    )
}
