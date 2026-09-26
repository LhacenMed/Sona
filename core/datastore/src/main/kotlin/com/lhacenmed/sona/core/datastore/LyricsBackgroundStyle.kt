package com.lhacenmed.sona.core.datastore

/** What the lyrics sheet is drawn over: ArchiveTune's `LyricsBackgroundStyle`. */
enum class LyricsBackgroundStyle {
    DEFAULT,
    FOLLOW_THEME,
    COLORING,

    /** The player's custom image - never chosen, only followed while the player has one. See [resolveFor]. */
    CUSTOM,
    ;

    /**
     * The style the lyrics are drawn in under [playerBackground]: the player's custom image while it has
     * one, and otherwise the chosen style - which is never [CUSTOM] once the player has let it go.
     */
    fun resolveFor(playerBackground: PlayerBackgroundStyle): LyricsBackgroundStyle =
        when {
            playerBackground == PlayerBackgroundStyle.CUSTOM -> CUSTOM
            this == CUSTOM -> DEFAULT
            else -> this
        }
}
