package com.lhacenmed.sona.core.datastore

/** How large subtitles are drawn while customized, on a phone held sideways. */
enum class SubtitleTextSize(val sp: Float) {
    SMALL(17f),
    MEDIUM(21f),
    LARGE(26f),
    ;

    companion object {
        val Default = MEDIUM
    }
}

/** What keeps customized subtitles readable over the picture. */
enum class SubtitleBackdrop {
    OUTLINE,
    SHADOW,
    BOX,
    ;

    companion object {
        val Default = SHADOW
    }
}

/** How far above the bottom of the screen customized subtitles stand while the controls are hidden. */
enum class SubtitlePosition(val bottomDp: Int) {
    LOW(12),
    MIDDLE(26),
    HIGH(56),
    ;

    companion object {
        val Default = MIDDLE
    }
}

/** The typeface subtitles are set in. */
enum class SubtitleFont {
    /** The device's own. */
    SYSTEM,

    /** The font the app itself is set in - see `AppFont`. */
    APP,

    /** A font of their own, picked from the device. */
    CUSTOM,
    ;

    companion object {
        /** What it is until the user chooses otherwise. */
        val Default = SYSTEM
    }
}
