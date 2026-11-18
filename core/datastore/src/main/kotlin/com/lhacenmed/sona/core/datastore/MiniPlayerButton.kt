package com.lhacenmed.sona.core.datastore

/** The button the mini player shows beside play and pause: Auxio's playback bar action. */
enum class MiniPlayerButton {
    SKIP_NEXT,
    REPEAT,
    SHUFFLE,
    FAVORITE,
    ;

    companion object {
        /** What it is until the user chooses otherwise. */
        val Default = SKIP_NEXT
    }
}
