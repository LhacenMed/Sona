package com.lhacenmed.sona.feature.video.scrub

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/** How close the player's position has to come to where a scrub let go for the seek bar to follow it again. */
private const val SettleToleranceMs = 1_000L

/** How long the seek bar waits for the player to catch up before following it wherever it is. */
private const val SettleTimeoutMs = 1_500L

/** How long the finger rests, snapped, before moving on again frees the thumb - see [VideoScrubState.dragTo]. */
private const val RestMs = 350L

/** How much of the snap radius a resting finger may tremble over and still be resting. */
private const val RestingShare = 4

/**
 * A scrub through the video - by the seek bar's thumb or by a swipe across the picture, one and the same to everything
 * that shows it. The video plays on meanwhile: only letting go moves it, and letting go where the scrub began - or snapped to
 * where the video is - leaves it be. Snapped to a bookmark instead, letting go goes to it exactly.
 * A scrub is through one video: should another take its place under the finger, it is called off - see [cancel].
 *
 * Each read of [targetMs] where it is drawn or laid out follows the finger without composing anything for it.
 */
@Stable
internal class VideoScrubState(
    private val onSeek: (Long) -> Unit,
    private val onSnapped: () -> Unit,
    private val now: () -> Long = SystemClock::uptimeMillis,
    /** Where letting go snapped to a bookmark goes: there exactly, rather than near it as [onSeek] may. */
    private val onSeekExactly: (Long) -> Unit = onSeek,
) {
    var isActive by mutableStateOf(false)
        private set

    /** Where the scrub began. */
    var originMs by mutableLongStateOf(0L)
        private set

    /** Where the finger has taken it to. */
    var targetMs by mutableLongStateOf(0L)
        private set

    /** The seek track's place on the screen, which the preview stands over. */
    var trackBounds by mutableStateOf(Rect.Zero)

    /** Where the last scrub let go, until the player has caught up with it - and how long that may take. */
    private var settlingMs: Long? = null
    private var settlingUntilMs = 0L

    /** Whether a scrub was called off with the finger still down: nothing it does counts until it lifts. */
    private var isCancelled by mutableStateOf(false)

    /** Whether a finger scrubs, or has yet to lift from a scrub called off - the controls under it stay meanwhile. */
    val isHeld: Boolean get() = isActive || isCancelled

    /**
     * Whether the thumb stands snapped to where the video is, playing on - YouTube's release to cancel: letting go there seeks
     * nowhere.
     */
    var isSnapped by mutableStateOf(false)
        private set

    /** Whether the thumb may snap the next time it comes near where the video is - see [dragTo]. */
    private var canSnap = false

    /** The bookmark the thumb stands snapped to - null where it stands at none. */
    var snappedBookmarkMs by mutableStateOf<Long?>(null)
        private set

    /** The bookmarks the thumb may snap to the next time it comes near: those it has been far enough from since it last did. */
    private val armedBookmarks = HashSet<Long>()
    private var hasArmedBookmarks = false

    /** Where the thumb was taken last - what a swipe passed over since is read from. */
    private var lastPositionMs = 0L

    /** Where the finger came to rest while snapped, and since when. */
    private var restPositionMs = 0L
    private var restingSinceMs = 0L

    fun begin(fromMs: Long) {
        if (isActive || isCancelled) return
        originMs = fromMs
        targetMs = fromMs
        settlingMs = null
        canSnap = false
        isSnapped = false
        snappedBookmarkMs = null
        armedBookmarks.clear()
        hasArmedBookmarks = false
        lastPositionMs = fromMs
        isActive = true
    }

    /** Takes the scrub to [positionMs]. */
    fun moveTo(positionMs: Long) {
        if (!isActive) return
        targetMs = positionMs
    }

    /**
     * Takes the scrub to [positionMs] under the thumb, the video playing at [playingMs] - YouTube's release to cancel. Having
     * gone further than [releaseRadiusMs] from where the video is, the thumb snaps to it on coming within [snapRadiusMs] - or
     * passing right over it, within [releaseRadiusMs] - however fast it comes, and stays with it while it moves on. It lets go
     * pulled out past [releaseRadiusMs], or moved on after resting there [RestMs]: the thumb is being aimed there, so it moves
     * freely, and snaps again only once it has left and come back. Each of [bookmarksMs] holds the thumb just the same - where
     * the video is first, should both be in reach.
     */
    fun dragTo(positionMs: Long, playingMs: Long, snapRadiusMs: Long, releaseRadiusMs: Long, bookmarksMs: List<Long> = emptyList()) {
        if (!isActive) return
        val nowMs = now()
        val distanceMs = abs(positionMs - playingMs)
        val lastMs = lastPositionMs
        lastPositionMs = positionMs
        // A fast swipe can leap past one between two touches, never landing within the snap radius.
        fun isReached(atMs: Long) = abs(positionMs - atMs) <= snapRadiusMs ||
            ((lastMs < atMs) != (positionMs < atMs) && abs(positionMs - atMs) <= releaseRadiusMs)
        // Those away from where the scrub began are in reach at once: a tap on one lands on it.
        if (!hasArmedBookmarks) {
            hasArmedBookmarks = true
            bookmarksMs.filterTo(armedBookmarks) { abs(it - originMs) > releaseRadiusMs }
        }
        val heldAtMs = if (isSnapped) playingMs else snappedBookmarkMs
        if (heldAtMs != null) {
            val hasMoved = abs(positionMs - restPositionMs) > snapRadiusMs / RestingShare
            if (abs(positionMs - heldAtMs) > releaseRadiusMs || (hasMoved && nowMs - restingSinceMs >= RestMs)) {
                isSnapped = false
                snappedBookmarkMs = null
            }
            if (hasMoved) rest(positionMs, nowMs)
        } else if (canSnap && isReached(playingMs)) {
            canSnap = false
            isSnapped = true
            rest(positionMs, nowMs)
            onSnapped()
        } else {
            armedBookmarks.filter(::isReached).minByOrNull { abs(positionMs - it) }?.let { bookmark ->
                armedBookmarks -= bookmark
                snappedBookmarkMs = bookmark
                rest(positionMs, nowMs)
                onSnapped()
            }
        }
        if (distanceMs > releaseRadiusMs) canSnap = true
        bookmarksMs.forEach { if (abs(positionMs - it) > releaseRadiusMs && it != snappedBookmarkMs) armedBookmarks += it }
        targetMs = if (isSnapped) playingMs else snappedBookmarkMs ?: positionMs
    }

    /** Counts the finger as resting at [positionMs] from [atMs] on, until it moves. */
    private fun rest(positionMs: Long, atMs: Long) {
        restPositionMs = positionMs
        restingSinceMs = atMs
    }

    /** Lets go, the video going where the scrub reached - or left playing on, back where it began. */
    fun end() {
        isCancelled = false
        if (!isActive) return
        isActive = false
        val isReleasedToCancel = isSnapped
        val bookmark = snappedBookmarkMs
        isSnapped = false
        snappedBookmarkMs = null
        if (isReleasedToCancel || targetMs == originMs) return
        settlingMs = targetMs
        settlingUntilMs = now() + SettleTimeoutMs
        if (bookmark != null) onSeekExactly(bookmark) else onSeek(targetMs)
    }

    /**
     * Calls off the scrub, and whatever it was settling to: the video it was through has gone. It seeks nowhere, and the finger
     * still down moves nothing until it lifts - nor does what it lets go of, a position in a video no longer there.
     */
    fun cancel() {
        if (isActive) isCancelled = true
        isActive = false
        isSnapped = false
        snappedBookmarkMs = null
        settlingMs = null
    }

    /** Where the bar stands, the player being at [playerMs]: with the video while snapped to it, never jumping back after letting go. */
    fun shownPositionMs(playerMs: Long): Long {
        if (isActive) return if (isSnapped) playerMs else targetMs
        val settling = settlingMs ?: return playerMs
        if (abs(playerMs - settling) <= SettleToleranceMs || now() >= settlingUntilMs) {
            settlingMs = null
            return playerMs
        }
        return settling
    }
}

/**
 * Where a preview [previewWidth] wide stands, centred over [thumbX] where it can be and kept [edgeMargin] inside a
 * screen [screenWidth] wide.
 */
internal fun previewLeft(thumbX: Float, previewWidth: Float, screenWidth: Float, edgeMargin: Float): Float {
    val furthest = (screenWidth - previewWidth - edgeMargin).coerceAtLeast(edgeMargin)
    return (thumbX - previewWidth / 2f).coerceIn(edgeMargin, furthest)
}
