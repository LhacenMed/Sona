package com.lhacenmed.sona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lhacenmed.sona.core.model.AudioFormat
import com.lhacenmed.sona.core.model.ClipRange
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.model.VideoFormat
import com.lhacenmed.sona.core.model.VideoQuality

/** One download asked for - queued, running, or over - with the choices it was asked with. See `Download`. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String?,
    val kind: String,
    val videoQuality: String,
    val audioFormat: String,
    @ColumnInfo(defaultValue = "'QUALITY'") val videoFormat: String = VideoFormat.QUALITY.name,
    val captions: Boolean,
    val skipSponsors: Boolean,
    val status: String,
    val error: String?,
    val createdAt: Long,
    val formatId: String? = null,
    val clipStart: Long? = null,
    val clipEnd: Long? = null,
    @ColumnInfo(defaultValue = "0") val splitChapters: Boolean = false,
    val commandArguments: String? = null,
    val failure: String? = null,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
    val site: String? = null,
    val durationSeconds: Long? = null,
    /** Every file it saved, one path a line. */
    @ColumnInfo(defaultValue = "''") val savedPaths: String = "",
    @ColumnInfo(defaultValue = "0") val sizeBytes: Long = 0,
    val finishedAt: Long? = null,
    val startAt: Long? = null,
)

fun DownloadEntity.toDomain() = Download(
    id = id,
    url = url,
    title = title,
    choices = DownloadChoices(
        kind = enumOr(kind, DownloadKind.VIDEO),
        videoQuality = enumOr(videoQuality, VideoQuality.P1080),
        audioFormat = enumOr(audioFormat, AudioFormat.ORIGINAL),
        videoFormat = enumOr(videoFormat, VideoFormat.QUALITY),
        captions = captions,
        skipSponsors = skipSponsors,
    ),
    options = DownloadOptions(
        formatId = formatId,
        clip = if (clipStart != null && clipEnd != null) ClipRange(clipStart, clipEnd) else null,
        splitChapters = splitChapters,
        commandArguments = commandArguments,
    ),
    // One running as the app was last stopped never finished: it is taken up again.
    status = enumOr(status, DownloadStatus.QUEUED).let { if (it == DownloadStatus.RUNNING) DownloadStatus.QUEUED else it },
    error = error,
    failure = failure?.let { enumOr(it, DownloadFailure.FAILED) },
    createdAt = createdAt,
    thumbnailUrl = thumbnailUrl,
    uploader = uploader,
    site = site,
    durationSeconds = durationSeconds,
    savedPaths = savedPaths.lines().filter(String::isNotEmpty),
    sizeBytes = sizeBytes,
    finishedAt = finishedAt,
    startAt = startAt,
)

/** A new download of [url] with [choices] and [options], queued - to start at [startAt], or as soon as it can. */
fun queuedDownload(
    url: String,
    title: String?,
    thumbnailUrl: String?,
    uploader: String?,
    choices: DownloadChoices,
    options: DownloadOptions,
    startAt: Long?,
    createdAt: Long,
) = DownloadEntity(
    url = url,
    title = title,
    kind = choices.kind.name,
    videoQuality = choices.videoQuality.name,
    audioFormat = choices.audioFormat.name,
    videoFormat = choices.videoFormat.name,
    captions = choices.captions,
    skipSponsors = choices.skipSponsors,
    status = DownloadStatus.QUEUED.name,
    error = null,
    createdAt = createdAt,
    formatId = options.formatId,
    clipStart = options.clip?.startSeconds,
    clipEnd = options.clip?.endSeconds,
    splitChapters = options.splitChapters,
    commandArguments = options.commandArguments,
    thumbnailUrl = thumbnailUrl,
    uploader = uploader,
    startAt = startAt,
)

private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T = enumValues<T>().firstOrNull { it.name == name } ?: default
