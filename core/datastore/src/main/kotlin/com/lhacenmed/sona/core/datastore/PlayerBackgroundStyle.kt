package com.lhacenmed.sona.core.datastore

/**
 * What the expanded player is drawn over: ArchiveTune's `PlayerBackgroundStyle`, in its order. [BLUR] needs
 * Android 12's blur, so it is only offered from there.
 */
enum class PlayerBackgroundStyle {
    /** The theme's own surface - ArchiveTune's "Follow theme". */
    FOLLOW_THEME,
    GRADIENT,
    CUSTOM,
    BLUR,
    COLORING,
    BLUR_GRADIENT,
    GLOW,
    GLOW_ANIMATED,
    ;

    companion object {
        /** What it is until the user chooses otherwise. */
        val Default = FOLLOW_THEME
    }
}
