package com.lhacenmed.sona.core.datastore

/**
 * What the expanded player and its lyrics sheet are drawn over: ArchiveTune's `PlayerBackgroundStyle`, in its
 * order save for [CUSTOM], last as the one that asks for an image of the user's own. [BLUR] needs Android 12's
 * blur, so it is only offered from there. Stored by name, so the order is only how the styles are listed.
 */
enum class PlayerBackgroundStyle {
    /** The theme's own surface - ArchiveTune's "Follow theme". */
    FOLLOW_THEME,
    GRADIENT,
    BLUR,
    COLORING,
    BLUR_GRADIENT,
    GLOW,
    GLOW_ANIMATED,
    CUSTOM,
    ;

    companion object {
        /** What it is until the user chooses otherwise. */
        val Default = FOLLOW_THEME
    }
}
