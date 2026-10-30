package com.lhacenmed.sona.feature.backup

import com.lhacenmed.sona.core.database.entity.PlaylistCoverSource
import com.lhacenmed.sona.core.model.sort.SortableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The format backups are written in. A backup in a later one is refused rather than half read. */
const val BACKUP_FORMAT_VERSION = 1

/** What a backup says of itself. */
@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val createdAt: Long,
    val appVersionCode: Long,
)

/**
 * Everything the user made in the library, with each track named by its file's path rather than by an id - so
 * it means the same on any device - and the library's files listed by fingerprint, for a restore to compare
 * this device against. The files themselves are not in a backup.
 */
@Serializable
data class BackupLibrary(
    val playlists: List<BackedUpPlaylist>,
    val favoriteCollections: List<BackedUpFavoriteCollection>,
    val arrangements: List<BackedUpArrangement>,
    val playStats: List<BackedUpPlayStats>,
    val resumePositions: List<BackedUpResumePosition>,
    val media: List<BackedUpMedia>,
)

/** A playlist, with [coverImage] the backup's own copy of its cover image, when it has one. */
@Serializable
data class BackedUpPlaylist(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val modifiedAt: Long,
    val coverSource: PlaylistCoverSource,
    val coverTrackPath: String?,
    val coverImage: String?,
    val tracks: List<BackedUpPlaylistTrack>,
)

@Serializable
data class BackedUpPlaylistTrack(
    val path: String,
    val addedAt: Long,
)

@Serializable
data class BackedUpFavoriteCollection(
    val collection: String,
    val favoritedAt: Long,
)

@Serializable
data class BackedUpArrangement(
    val list: SortableList,
    val instanceId: String,
    val path: String,
    val position: Int,
)

@Serializable
data class BackedUpPlayStats(
    val path: String,
    val playCount: Int,
    val lastPlayedAt: Long,
)

@Serializable
data class BackedUpResumePosition(
    val path: String,
    val positionMs: Long,
)

/**
 * A library file as it was backed up: what its bytes were - [fingerprint] - and where it lay. Every library file is
 * listed, so a restore finds each one a playlist or the history names; [isKeptInDrive] says whether its folder was
 * one the user backs up to Drive, which only those are.
 */
@Serializable
data class BackedUpMedia(
    val fingerprint: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val isVideo: Boolean,
    val isKeptInDrive: Boolean = true,
)

/** One stored setting's value, of each kind a settings file holds. */
@Serializable
sealed interface BackedUpSetting {
    val value: Any

    @Serializable
    @SerialName("boolean")
    data class BooleanSetting(override val value: Boolean) : BackedUpSetting

    @Serializable
    @SerialName("int")
    data class IntSetting(override val value: Int) : BackedUpSetting

    @Serializable
    @SerialName("long")
    data class LongSetting(override val value: Long) : BackedUpSetting

    @Serializable
    @SerialName("float")
    data class FloatSetting(override val value: Float) : BackedUpSetting

    @Serializable
    @SerialName("string")
    data class StringSetting(override val value: String) : BackedUpSetting

    @Serializable
    @SerialName("stringSet")
    data class StringSetSetting(override val value: Set<String>) : BackedUpSetting
}

@Suppress("UNCHECKED_CAST")
internal fun backedUpSettingOf(value: Any): BackedUpSetting = when (value) {
    is Boolean -> BackedUpSetting.BooleanSetting(value)
    is Int -> BackedUpSetting.IntSetting(value)
    is Long -> BackedUpSetting.LongSetting(value)
    is Float -> BackedUpSetting.FloatSetting(value)
    is String -> BackedUpSetting.StringSetting(value)
    is Set<*> -> BackedUpSetting.StringSetSetting(value as Set<String>)
    else -> error("A setting of type ${value::class.simpleName} cannot be backed up")
}
