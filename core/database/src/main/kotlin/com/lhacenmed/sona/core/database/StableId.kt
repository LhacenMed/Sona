package com.lhacenmed.sona.core.database

import java.text.Normalizer
import java.util.Locale

/**
 * A deterministic 64-bit id derived from the parts that identify an entity.
 *
 * Two properties matter, and neither is optional:
 *  - **Stable across scans.** The same file/album/artist must map to the same id every time, or
 *    every rescan churns the whole database and breaks anything that stored an id (the playback
 *    queue, most obviously).
 *  - **Distinguishable from a MediaStore id.** MediaStore hands out small positive ids; synthetic
 *    ids for entities MediaStore never returned (manually walked files) must not collide with them,
 *    so the top bit is always set, putting every synthetic id in the negative Long range.
 *
 * FNV-1a is used rather than [String.hashCode] because hashCode is only 32 bits - which collides in
 * practice at library scale (~50% odds somewhere around 77,000 names by the birthday bound), where
 * 64 bits does not.
 */
fun stableIdOf(vararg parts: String): Long {
    var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
    for (part in parts) {
        for (char in part) {
            hash = hash xor char.code.toLong()
            hash *= 0x100000001b3L // FNV-1a 64-bit prime
        }
        // Separator so ("ab", "c") and ("a", "bc") cannot hash alike.
        hash = hash xor 0x1FL
        hash *= 0x100000001b3L
    }
    return hash or Long.MIN_VALUE
}

/** The marks a name can carry that nothing shows: right-to-left and joining marks, a byte order mark, zero-width spaces. */
private val InvisibleMarks = Regex("""\p{Cf}""")

private val Spacing = Regex("""\s+""")

/**
 * What tells one name from another, the one place it is decided: what the name reads as - whatever its case, Auxio's
 * `rawName.lowercase()`, however its letters are encoded, and whatever marks or extra spaces nothing shows. Two
 * spellings with the same key are one artist, album or genre, since each takes its id from this.
 */
fun nameKey(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFKC)
        .replace(InvisibleMarks, "")
        .replace(Spacing, " ")
        .trim()
        .lowercase(Locale.ROOT)

/** An artist's id, derived from its name. */
fun artistIdOf(name: String): Long = stableIdOf("artist", nameKey(name))

/** An album's id, derived from the artist and title that tell it apart from every other album. */
fun albumIdOf(artistName: String, title: String): Long = stableIdOf("album", nameKey(artistName), nameKey(title))

/** A genre's id, derived from its name. */
fun genreIdOf(name: String): Long = stableIdOf("genre", nameKey(name))
