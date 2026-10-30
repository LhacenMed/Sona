package com.lhacenmed.sona.core.datastore

/**
 * How the expanded player lays itself out.
 *
 * Each entry is drawn by its own files under `feature/player/.../player/style/`, and looked up in
 * `PlayerStyles.kt` there - whose `when`s are exhaustive, so a new entry names every place it needs.
 */
enum class PlayerStyle {
    /** The player with its lyrics in a sheet of their own, opened from the queue bar. */
    DEFAULT,

    /** The player with its lyrics in place, grown from the line above the seek bar. */
    IMMERSIVE,
    ;

    companion object {
        /**
         * What it is until the user chooses otherwise - and for a style no longer offered, as the former
         * `CINEMATIC` is, so everyone it was chosen for comes back to this one.
         */
        val Default = DEFAULT
    }
}
