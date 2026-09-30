package com.lhacenmed.sona.core.datastore

/** What the lyrics sheet is drawn over: ArchiveTune's `LyricsBackgroundStyle`. */
enum class LyricsBackgroundStyle {
    /** The cover, blurred, under a gradient of its colours. */
    BLURRED_COVER,
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
            this == CUSTOM -> Default
            else -> this
        }

    companion object {
        /** What it is until the user chooses otherwise. */
        val Default = FOLLOW_THEME
    }
}
