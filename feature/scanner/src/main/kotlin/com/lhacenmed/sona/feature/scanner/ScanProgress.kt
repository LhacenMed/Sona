package com.lhacenmed.sona.feature.scanner

/** The steps of a scan, in the order it takes them. */
enum class ScanStep {
    /** MediaStore's already-indexed audio, read and published. */
    READING_MEDIA_STORE,

    /** Storage walked for audio files MediaStore has not indexed. */
    SEARCHING_STORAGE,

    /** The tags of each file the walk found, read one by one - the only step whose size is known. */
    READING_TAGS,

    /** The result written to the library. */
    SAVING,
}

/** Where a running scan is: its [step], and - while [ScanStep.READING_TAGS] - [done] of [total] files. */
data class ScanProgress(val step: ScanStep, val done: Int = 0, val total: Int = 0) {
    /** 0f–1f while the step's size is known, null while it is not. */
    val fraction: Float? get() = if (total > 0) done.toFloat() / total else null
}
