package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lhacenmed.sona.core.model.DownloadCommand
import com.lhacenmed.sona.core.model.DownloadWatch

/** A saved set of yt-dlp arguments - see `DownloadCommand`. */
@Entity(tableName = "download_commands")
data class DownloadCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val arguments: String,
)

fun DownloadCommandEntity.toDomain() = DownloadCommand(id, name, arguments)

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
