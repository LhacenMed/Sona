package com.lhacenmed.sona.feature.backup

import com.lhacenmed.sona.core.model.FolderPicks
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.backup.media.MissingMedia

/**
 * The files a restore can bring back from Drive - those this device lacks that Drive keeps - as the folder picker
 * offers them: [music] and [videos], keyed by their kind as [BackupFilePicks] are. None is on this device yet, so
 * each is named by its file, with the size and length the backup recorded.
 */
class RestoreFiles(missing: List<MissingMedia>) {

    private val fingerprintByPath = missing.associate { it.media.path to it.media.fingerprint }

    val music: List<Track>
    val videos: List<Track>

    init {
        val files = missing.mapIndexed { index, item -> item.toTrack(index.toLong()) }.partition { !it.isVideo }
        music = files.first
        videos = files.second
    }

    private val musicPicks = FolderPicks(music, BackupFolders.MUSIC_KEY_PREFIX)
    private val videoPicks = FolderPicks(videos, BackupFolders.VIDEOS_KEY_PREFIX)

    /** The keys picking every file. */
    val all: Set<String> = musicPicks.allBut(emptySet()) + videoPicks.allBut(emptySet())

    fun isEmpty(): Boolean = fingerprintByPath.isEmpty()

    fun pickedMusic(keys: Set<String>): List<Track> = music.filter { musicPicks.isPicked(it, keys) }

    fun pickedVideos(keys: Set<String>): List<Track> = videos.filter { videoPicks.isPicked(it, keys) }

    /** The fingerprints of the files [keys] pick - what [BackupRepository.chooseFilesToRestore] takes. */
    fun fingerprintsOf(keys: Set<String>): Set<String> =
        (pickedMusic(keys) + pickedVideos(keys)).mapTo(HashSet()) { fingerprintByPath.getValue(it.path) }
}

private fun MissingMedia.toTrack(id: Long) = Track(
    id = id,
    mediaStoreId = 0L,
    title = media.path.substringAfterLast('/').substringBeforeLast('.'),
    artist = "",
    artistId = 0L,
    album = "",
    albumId = 0L,
    genre = null,
    genreId = null,
    path = media.path,
    folderPath = media.path.substringBeforeLast('/'),
    durationMs = media.durationMs,
    trackNumber = null,
    discNumber = null,
    year = null,
    dateAddedSeconds = 0L,
    coverArtUri = null,
    isManuallyScanned = false,
    isVideo = media.isVideo,
    sizeBytes = media.sizeBytes,
)
