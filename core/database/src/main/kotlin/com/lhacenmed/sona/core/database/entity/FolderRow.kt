package com.lhacenmed.sona.core.database.entity

import com.lhacenmed.sona.core.model.Folder

/**
 * Projection of the folders aggregate query - folders are derived from `tracks.folderPath`, not
 * stored, so there is no folder table and no folder entity.
 */
data class FolderRow(
    val path: String,
    val trackCount: Int,
)

/** How many of a folder's tracks share one cover, which is what ranks a folder's composed cover. */
data class FolderCoverRow(
    val path: String,
    val coverArtUri: String,
    val trackCount: Int,
)

fun FolderRow.toDomain(coverArtUris: List<String>) = Folder(
    path = path,
    name = path.substringAfterLast('/'),
    trackCount = trackCount,
    coverArtUris = coverArtUris,
)
