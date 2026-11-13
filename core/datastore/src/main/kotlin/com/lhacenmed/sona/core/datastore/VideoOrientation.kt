package com.lhacenmed.sona.core.datastore

/** Which way the video player's screen stands - the states its rotation button goes round: turning, held, held the other way. */
enum class VideoOrientation {
    /** Turns with the device. */
    AUTO,

    /** Held upright, however the device is turned. */
    PORTRAIT,

    /** Held on its side, either way round, however the device is turned. */
    LANDSCAPE,
    ;

    /**
     * What the rotation button moves to from this one: from turning, held as the screen stands now - [isLandscape] or
     * not; from that first hold, [firstHeld], turned to the other way; and from there, turning again.
     */
    fun next(isLandscape: Boolean, firstHeld: VideoOrientation?): VideoOrientation = when (this) {
        AUTO -> if (isLandscape) LANDSCAPE else PORTRAIT
        firstHeld -> opposite
        else -> AUTO
    }

    private val opposite: VideoOrientation
        get() = when (this) {
            AUTO -> AUTO
            PORTRAIT -> LANDSCAPE
            LANDSCAPE -> PORTRAIT
        }

    companion object {
        /** What every video opens at. */
        val Default = AUTO
    }
}
