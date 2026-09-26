package com.lhacenmed.sona.core.model

/**
 * The typeface the app's text is set in: ArchiveTune's `AppFontPreference`, with Auxio's typeface beside it.
 * [DEFAULT] is ArchiveTune's own, Poppins; [INTER] is Auxio's; [CUSTOM] is a `.ttf` the user picked.
 */
enum class AppFont {
    DEFAULT,
    SYSTEM,
    INTER,
    CUSTOM,
}
