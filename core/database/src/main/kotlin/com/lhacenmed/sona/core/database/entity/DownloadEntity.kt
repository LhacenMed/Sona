package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lhacenmed.sona.core.model.AudioFormat
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadStatus
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
    val captions: Boolean,
    val skipSponsors: Boolean,
    val status: String,
    val error: String?,
    val createdAt: Long,
)

fun DownloadEntity.toDomain() = Download(
    id = id,
    url = url,
    title = title,
    choices = DownloadChoices(
        kind = enumOr(kind, DownloadKind.VIDEO),
        videoQuality = enumOr(videoQuality, VideoQuality.P1080),
        audioFormat = enumOr(audioFormat, AudioFormat.ORIGINAL),
        captions = captions,
        skipSponsors = skipSponsors,
    ),
    // One running as the app was last stopped never finished: it is taken up again.
    status = enumOr(status, DownloadStatus.QUEUED).let { if (it == DownloadStatus.RUNNING) DownloadStatus.QUEUED else it },
    error = error,
    createdAt = createdAt,
)

/** A new download of [url] with [choices], queued. */
fun queuedDownload(url: String, title: String?, choices: DownloadChoices, createdAt: Long) = DownloadEntity(
    url = url,
    title = title,
    kind = choices.kind.name,
    videoQuality = choices.videoQuality.name,
    audioFormat = choices.audioFormat.name,
    captions = choices.captions,
    skipSponsors = choices.skipSponsors,
    status = DownloadStatus.QUEUED.name,
    error = null,
    createdAt = createdAt,
)

private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T = enumValues<T>().firstOrNull { it.name == name } ?: default
