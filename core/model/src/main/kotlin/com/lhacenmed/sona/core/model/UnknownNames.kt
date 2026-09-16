package com.lhacenmed.sona.core.model

/**
 * What a track, album, artist or genre is called when the file never said: Auxio's `def_artist`,
 * `def_album` and `def_genre`.
 *
 * The scanner writes these names rather than MediaStore's own `<unknown>`, so every screen, the
 * notification included, already holds the name it should show and no screen has to know about the
 * placeholder. Tracks that name no genre are gathered under [GENRE] the way Auxio gathers them, so
 * they are browsable rather than missing from the genres tab entirely.
 *
 * A name being one of these is also what puts it before every real name when a list is sorted
 * ascending - Auxio's `Name.Unknown` ordering - which is why they are constants rather than strings
 * written at each site.
 */
object UnknownNames {
    const val ARTIST = "Unknown artist"
    const val ALBUM = "Unknown album"
    const val GENRE = "Unknown genre"
}
