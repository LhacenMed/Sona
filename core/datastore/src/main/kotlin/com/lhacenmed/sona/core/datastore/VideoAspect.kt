package com.lhacenmed.sona.core.datastore

/**
 * How a video is fitted to the screen - the modes its aspect button steps through, in the order listed.
 *
 * [ratio] is the frame's width over its height for the modes that force one, null for those that follow
 * the video or the screen.
 */
enum class VideoAspect(val ratio: Float? = null) {
    /** Fills the screen with the video whole, cut at the edges it overflows. */
    CROP,

    /** Fills the screen, the video's own shape ignored. */
    STRETCH,

    RATIO_16_9(16f / 9f),
    RATIO_18_9(18f / 9f),
    RATIO_4_3(4f / 3f),

    /** At the video's own size in pixels. */
    ORIGINAL,

    /** As large as the screen allows with the whole video showing. */
    FIT,
    ;

    /** The mode the aspect button moves to from this one. */
    fun next(): VideoAspect = entries[(ordinal + 1) % entries.size]

    companion object {
        /** What it is until the user chooses otherwise. */
        val Default = FIT
    }
}
