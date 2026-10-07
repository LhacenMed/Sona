package com.lhacenmed.sona.core.model.sort

/** What a list is ordered by. Which of these a given list offers is up to that list. */
enum class SortCriterion {
    /**
     * The order the user arranged by dragging - every collection's tracks can have one. Tracks added
     * after it was arranged are placed by the order's direction: see [SortOrder].
     */
    CUSTOM,
    NAME,
    ARTIST,
    ALBUM,

    /** Release year - the only release date the library records. */
    YEAR,
    DURATION,

    /** Disc, then track number: the order an album plays in. */
    TRACK_NUMBER,
    TRACK_COUNT,
    ALBUM_COUNT,

    /** When the item itself came to be: a track's file joining the library, a playlist's last change. */
    DATE,

    /** When the item joined the list it is shown in: the library, or the playlist it sits in. */
    DATE_ADDED,
}
