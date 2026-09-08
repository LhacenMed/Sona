package com.lhacenmed.sona.feature.playback

import com.lhacenmed.sona.core.model.RepeatMode

/**
 * Snapshot of playback state exposed to the UI layer.
 */
data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val currentTrackId: Long? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Long> = emptyList(), // ordered track ids currently queued
)
