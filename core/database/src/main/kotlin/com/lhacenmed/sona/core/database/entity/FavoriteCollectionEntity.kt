package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A collection favorited as itself - an album, an artist, a genre, a folder or a playlist - for Favorites to
 * list it, one tap away. Favorited tracks are Favorites' own tracks instead: membership of its playlist.
 *
 * [collection] is the collection's identity as `PlaybackParent.toStorageKey` writes it, which stays the same
 * across rescans. No foreign key reaches it: a collection gone from the library simply is not listed, and
 * comes back listed with it; a deleted playlist takes its row with it - see `PlaylistDao.delete`.
 */
@Entity(tableName = "favorite_collections")
data class FavoriteCollectionEntity(
    @PrimaryKey val collection: String,
    val favoritedAt: Long,
)
