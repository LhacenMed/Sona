package com.lhacenmed.sona.core.datastore

/** How album covers are loaded, from not at all to exactly as the file holds them. Ported from Auxio. */
enum class CoverMode {
    OFF,
    SAVE_SPACE,
    BALANCED,
    HIGH_QUALITY,
    AS_IS,
}
