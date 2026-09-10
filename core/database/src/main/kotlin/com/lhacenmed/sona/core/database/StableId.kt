package com.lhacenmed.sona.core.database

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
