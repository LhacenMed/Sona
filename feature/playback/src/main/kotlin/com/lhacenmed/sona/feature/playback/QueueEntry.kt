package com.lhacenmed.sona.feature.playback

/**
 * One slot of the queue.
 *
 * [key] tells apart two slots holding the same track, so a list of the queue keeps each row's identity
 * while rows move. [mediaItemIndex] is where the slot sits in the player's own order, which is what every
 * command on the slot addresses.
 */
data class QueueEntry(
    val key: String,
    val mediaItemIndex: Int,
    val trackId: Long,
)
