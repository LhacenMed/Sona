package com.lhacenmed.sona.core.datastore

/** What the mini player is drawn over: ArchiveTune's `MiniPlayerBackgroundStyle`. */
enum class MiniPlayerBackgroundStyle {
    /** The theme's own surface - ArchiveTune's "Follow theme". */
    FOLLOW_THEME,
    GRADIENT,
    GLOW,
    ;

    companion object {
        /** What it is until the user chooses otherwise. */
        val Default = FOLLOW_THEME
    }
}
