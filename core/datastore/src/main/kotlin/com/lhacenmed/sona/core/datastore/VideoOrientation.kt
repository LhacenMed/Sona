package com.lhacenmed.sona.core.datastore

/** Which way the video player's screen stands - the three states its rotation button steps through. */
enum class VideoOrientation {
    /** Turns with the device. */
    AUTO,

    /** Held upright, however the device is turned. */
    PORTRAIT,

    /** Held on its side, either way round, however the device is turned. */
    LANDSCAPE,
    ;

    /** The state the rotation button moves to from this one. */
    fun next(): VideoOrientation = entries[(ordinal + 1) % entries.size]

    companion object {
        /** What it is until the user chooses otherwise. */
        val Default = AUTO
    }
}
