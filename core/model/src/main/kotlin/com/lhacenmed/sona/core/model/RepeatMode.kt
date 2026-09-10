package com.lhacenmed.sona.core.model

/**
 * User-selectable queue repeat behavior.
 *
 * Domain-level counterpart of media3's `Player.REPEAT_MODE_*` int constants - kept here (rather
 * than nested inside a settings class) since both `:feature:playback` and `:feature:player`
 * reference it.
 */
enum class RepeatMode { OFF, ALL, ONE }
