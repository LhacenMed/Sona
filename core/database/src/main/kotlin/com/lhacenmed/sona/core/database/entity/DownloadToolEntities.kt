package com.lhacenmed.sona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lhacenmed.sona.core.model.DownloadCommand
import com.lhacenmed.sona.core.model.DownloadCommandShortcut
import com.lhacenmed.sona.core.model.DownloadWatch

/** A saved set of yt-dlp arguments - see `DownloadCommand`. */
@Entity(tableName = "download_commands")
data class DownloadCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val arguments: String,
    @ColumnInfo(defaultValue = "0") val addsToAudio: Boolean = false,
    @ColumnInfo(defaultValue = "0") val addsToVideo: Boolean = false,
    @ColumnInfo(defaultValue = "0") val addsToReading: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isPreferred: Boolean = false,
    /** The links it is for, one pattern a line - every link while none. */
    @ColumnInfo(defaultValue = "''") val urlPatterns: String = "",
)

fun DownloadCommandEntity.toDomain() =
    DownloadCommand(id, name, arguments, addsToAudio, addsToVideo, addsToReading, isPreferred, urlPatterns.lines().filter(String::isNotBlank))

fun DownloadCommand.toEntity() = DownloadCommandEntity(
    id = id,
    name = name,
    arguments = arguments,
    addsToAudio = addsToAudio,
    addsToVideo = addsToVideo,
    addsToReading = addsToReading,
    isPreferred = isPreferred,
    urlPatterns = urlPatterns.joinToString("\n"),
)

/** A snippet of yt-dlp arguments - see `DownloadCommandShortcut`. */
@Entity(tableName = "download_command_shortcuts")
data class DownloadCommandShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
)

fun DownloadCommandShortcutEntity.toDomain() = DownloadCommandShortcut(id, content)

/** A channel or playlist whose new videos are downloaded - see `DownloadWatch`. */
@Entity(tableName = "download_watches")
data class DownloadWatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    /** The ids of the videos it held when last looked at, one a line. */
    val knownIds: String,
    val createdAt: Long,
)

fun DownloadWatchEntity.toDomain() = DownloadWatch(id, url, title, knownIds.lines().filter(String::isNotEmpty).toSet(), createdAt)
