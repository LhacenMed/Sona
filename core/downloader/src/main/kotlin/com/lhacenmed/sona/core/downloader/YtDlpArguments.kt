package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind

/** How yt-dlp is asked to say what it does - one line each, Sona's own, apart from everything else it writes. */
internal object YtDlpLines {
    const val ITEM = "SONA_ITEM"
    const val PROGRESS = "SONA_PROGRESS"
    const val SAVED = "SONA_SAVED"
}

/** SponsorBlock's segments a download leaves out, when it skips sponsors. */
private const val SkippedSegments = "sponsor,selfpromo,interaction,music_offtopic"

/** A music cover cut to a square from the middle of a video's wide frame. */
private const val SquareCover = "ThumbnailsConvertor+ffmpeg_o:-c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\""

/**
 * What yt-dlp is told to download with these choices - the one place each choice becomes yt-dlp's, so a choice added is a
 * line added here. [captionLanguage] is the device's language, whose captions are kept with English's.
 */
internal fun DownloadChoices.toArguments(captionLanguage: String): List<String> = buildList {
    when (kind) {
        DownloadKind.VIDEO -> {
            addAll(listOf("-f", "bv*+ba/b"))
            videoQuality.height?.let { addAll(listOf("-S", "res:$it")) }
            addAll(listOf("--merge-output-format", "mp4"))
            // In the video itself, as tracks of its own, which every player reads.
            if (captions) {
                addAll(listOf("--write-subs", "--write-auto-subs", "--sub-langs", "$captionLanguage.*,en.*,-live_chat", "--convert-subs", "srt", "--embed-subs"))
            }
        }

        DownloadKind.AUDIO -> {
            addAll(listOf("-f", "ba/b", "-x", "--audio-quality", "0"))
            audioFormat.extension?.let { addAll(listOf("--audio-format", it)) }
            addAll(listOf("--embed-thumbnail", "--convert-thumbnails", "jpg", "--ppa", SquareCover))
            // A song's own title and artist where the site knows them - YouTube Music's - the video's and its channel's otherwise.
            addAll(listOf("--parse-metadata", "%(track,title)s:%(meta_title)s", "--parse-metadata", "%(artist,uploader)s:%(meta_artist)s"))
        }
    }
    addAll(listOf("--embed-metadata", "--embed-chapters", "--parse-metadata", "%(release_year,upload_date)s:%(meta_date)s"))
    if (skipSponsors) addAll(listOf("--sponsorblock-remove", SkippedSegments))
}

/**
 * What yt-dlp is told about every download, whatever was chosen: to save to [outputDir] by way of [tempDir], and to say
 * each item, its progress and each file saved - with its Most replayed graph and its chapters - on lines of Sona's own.
 */
internal fun downloadArguments(outputDir: String, tempDir: String): List<String> = listOf(
    "-P", "home:$outputDir",
    "-P", "temp:$tempDir",
    "-o", "%(title).150B [%(id)s].%(ext)s",
    // A link to one video in a playlist is the video: a playlist's own link is still the playlist.
    "--no-playlist",
    "--no-mtime",
    "--progress",
    "--progress-template", "download:${YtDlpLines.PROGRESS} %(progress.downloaded_bytes)s %(progress.total_bytes,progress.total_bytes_estimate)s",
    "--print", "before_dl:${YtDlpLines.ITEM} %(playlist_index|)s %(n_entries|)s %(title)s",
    "--print", "after_move:${YtDlpLines.SAVED} %(.{filepath,heatmap,chapters})j",
)
