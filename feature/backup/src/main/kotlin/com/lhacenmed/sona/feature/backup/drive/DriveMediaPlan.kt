package com.lhacenmed.sona.feature.backup.drive

import com.lhacenmed.sona.feature.backup.BackedUpMedia

/**
 * A library file kept in Drive.
 *
 * @property identity what its bytes are: their fingerprint, or - sealed - the name made from it with the key.
 * @property pathId what its path was when it was last sent, kept hashed: Drive holds little beside a file, and a sealed
 *   file says nothing of where it came from. Null for a file sent before it was kept.
 * @property isWhole whether Drive's own checksum shows it holds the bytes it should.
 * @property isDamaged whether that checksum shows it does not. A file can be neither: one sealed before its checksum was kept.
 */
internal data class StoredMedia(
    val id: String,
    val name: String,
    val identity: String,
    val pathId: String?,
    val isWhole: Boolean,
    val isDamaged: Boolean,
)

/** A library file a backup wants in Drive, under the [name] it would be seen by there. */
internal data class WantedMedia(
    val media: BackedUpMedia,
    val identity: String,
    val name: String,
    val pathId: String,
)

/** What to do for one [WantedMedia]. */
internal sealed interface MediaStep {
    val wanted: WantedMedia

    /** Drive has its bytes, whole: at most its name and path need to catch up - a move or a rename costs no upload. */
    data class Keep(override val wanted: WantedMedia, val stored: StoredMedia) : MediaStep {
        val needsDetails: Boolean get() = stored.name != wanted.name || stored.pathId != wanted.pathId
    }

    /** Its bytes go up: over the file [replacing], where it updates one, or as a new one. */
    data class Upload(override val wanted: WantedMedia, val replacing: StoredMedia?) : MediaStep
}

/**
 * How to bring [stored] to what [wanted] asks, so Drive ends with one file for each:
 * 1. a file with its bytes, whole, is kept - wherever the file now lies and whatever it is now called;
 * 2. a file with its bytes that is not whole is sent again over itself;
 * 3. a file from its path whose bytes nothing wants is the older version of it - its tags changed - and is updated;
 * 4. otherwise it is new.
 *
 * [wanted] holds each identity once.
 */
internal fun planDriveMedia(wanted: List<WantedMedia>, stored: Collection<StoredMedia>): List<MediaStep> {
    val byIdentity = stored.associateBy { it.identity }
    val wantedIdentities = wanted.mapTo(HashSet()) { it.identity }
    val olderVersions = stored.filter { it.identity !in wantedIdentities && it.pathId != null }.associateByTo(HashMap()) { it.pathId }
    return wanted.map { item ->
        val sameBytes = byIdentity[item.identity]
        when {
            sameBytes?.isWhole == true -> MediaStep.Keep(item, sameBytes)
            sameBytes != null -> MediaStep.Upload(item, replacing = sameBytes)
            else -> MediaStep.Upload(item, replacing = olderVersions.remove(item.pathId))
        }
    }
}
