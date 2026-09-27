package com.lhacenmed.sona.feature.update

/**
 * Lifecycle of the in-app APK update. There is only ever one update in flight, so this is a single
 * global state rather than a keyed map.
 */
sealed class UpdateState {
    data object Idle       : UpdateState()
    data object Connecting : UpdateState()

    /**
     * [receivedBytes] of the APK so far, of [totalBytes] - null while the server has not said - arriving at
     * [bytesPerSecond] over the last few seconds.
     */
    data class Downloading(val receivedBytes: Long, val totalBytes: Long?, val bytesPerSecond: Long) : UpdateState() {
        /** 0f–1f once the size is known, null before that. */
        val progress: Float? get() = totalBytes?.let { (receivedBytes.toFloat() / it).coerceIn(0f, 1f) }
    }

    /** [staged] is verified - see [ApkVerifier]. */
    data class  Downloaded(val staged: StagedApk) : UpdateState()
    data class  Error(val message: String) : UpdateState()
}
