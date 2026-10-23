package com.lhacenmed.sona.core.navigation

/**
 * What a screen opened from outside the app - the home screen widget - does once it is in, on the list the
 * queue plays from. Once only: a screen recreated after it has done it stays as it is.
 */
enum class ScreenArrival {
    /** Brings the playing row into view and ripples it as if pressed, so the eye lands on it. */
    RevealPlaying,

    /** Opens the full player over the screen. */
    ExpandPlayer,
}
