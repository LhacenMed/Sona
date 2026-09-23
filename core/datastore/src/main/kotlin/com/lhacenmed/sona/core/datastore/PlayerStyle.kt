package com.lhacenmed.sona.core.datastore

/**
 * How the expanded player lays itself out.
 *
 * Each entry is drawn by its own files under `feature/player/.../player/style/`, and looked up in
 * `PlayerStyles.kt` there - whose `when`s are exhaustive, so a new entry names every place it needs.
 */
enum class PlayerStyle {
    /** ArchiveTune's Cinematic player. */
    DEFAULT,
}
