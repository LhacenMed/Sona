package com.lhacenmed.sona.core.model

/**
 * User-selectable queue repeat behavior.
 *
 * Domain-level counterpart of media3's `Player.REPEAT_MODE_*` int constants - kept here (rather
 * than nested inside a settings class) since both `:feature:playback` and `:feature:player`
 * reference it.
 */
enum class RepeatMode {
    OFF,
    ALL,
    ONE,

    /**
     * Play the current track once more, then stop.
     *
     * Ported from Fossify's `PlaybackSetting.STOP_AFTER_CURRENT_TRACK`. It is a repeat *mode* rather
     * than a setting alongside [ONE], because the two are alternatives the user chooses between -
     * modelling it as a flag left it unreachable, since a flag has nowhere to live in a button that
     * cycles.
     *
     * Both this and [ONE] are `Player.REPEAT_MODE_ONE` to the player, so the player's own repeat int
     * can no longer tell them apart. The stored mode is the authority; see `RepeatModeMapping`.
     */
    STOP_AFTER_CURRENT,
    ;

    /** The mode the repeat button moves to next, in the order Fossify cycles them. */
    val next: RepeatMode
        get() = when (this) {
            OFF -> ALL
            ALL -> ONE
            ONE -> STOP_AFTER_CURRENT
            STOP_AFTER_CURRENT -> OFF
        }
}
