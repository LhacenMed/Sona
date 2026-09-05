package com.lhacenmed.sona.feature.playback

/**
 * Minimal snapshot of playback state exposed to the UI layer.
 */
data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val currentTrackId: Long? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)
