package com.lhacenmed.sona.feature.player.swiper

import android.content.Context
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.Dp
import androidx.viewpager2.widget.ViewPager2
import com.lhacenmed.sona.core.designsystem.effect.SonaEffects
import com.lhacenmed.sona.feature.player.QueueTrack
import kotlin.math.abs

/**
 * How long the cover takes to slide one page, when something other than a finger moves it - Auxio's
 * duration. Public so what a track change sets off elsewhere can wait for the slide to be over.
 */
const val CoverSlideDurationMillis = 300

/**
 * The player's covers: the whole queue as pages, swiped to move through it. Auxio's playback pager
 * (`PlaybackPanelFragment`), set up and driven the way Auxio sets it up and drives it.
 *
 * A page is a queue slot, so the page the user settles on *is* the track to play: a swipe asks for
 * that slot outright rather than for "next" or "previous", however fast or far the swipes come. And
 * whatever else moves the player - its skip buttons, the notification, a track ending - [show] turns
 * the pager after it: one page at a time with Auxio's slide, any other move in one jump.
 *
 * One difference from Auxio, because Sona's player answers later: Auxio's player moves in the same
 * call that asks it to, but Sona's reports the move back asynchronously. So the pager is never
 * turned under a finger, and never back to a track a swipe has already left - otherwise that late
 * report scrolls the pager back, and [smoothScrollByPageTo] stops the swipe in progress to do it.
 */
internal class QueueCoverPager(
    context: Context,
    cornerRadius: Dp,
) : FrameLayout(context) {

    /** Asked to play the slot a swipe settled on. */
    var onSwipeToTrack: (QueueTrack) -> Unit = {}

    /** Told a cover was double-tapped, and whether on the half that seeks backward. */
    var onDoubleTap: (isBackward: Boolean) -> Unit = {}

    private val coverAdapter = CoverPagerAdapter(cornerRadius) { isBackward -> onDoubleTap(isBackward) }
    private val pager = ViewPager2(context)
    private val carousel = CarouselTransformer()
    private val overscroll = CoverOverscroll(pager, carousel)

    /** The queue the player plays, and where in it the player says it is. */
    private var playerQueue: List<QueueTrack> = emptyList()
    private var playerIndex = -1

    /** The page a swipe asked the player for, until the player says it is there. */
    private var swipedIndex: Int? = null

    /**
     * Whether a finger holds the pager - from the drag starting to the page coming to rest. Tracked
     * here rather than read from [ViewPager2.getScrollState], which a jump made with
     * `setCurrentItem(item, false)` can leave settling when the pager has nothing to scroll.
     *
     * Every drag is a finger's: the only fake drag is the band's, relaying a finger that swiped past an
     * end and came back ([CoverOverscroll]) - a swipe like any other, so it turns the track too.
     */
    private var isUserSwiping = false

    init {
        pager.apply {
            adapter = coverAdapter
            registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageScrollStateChanged(state: Int) {
                        when (state) {
                            ViewPager2.SCROLL_STATE_DRAGGING -> isUserSwiping = true
                            ViewPager2.SCROLL_STATE_IDLE -> {
                                isUserSwiping = false
                                // Whatever the player reported while a finger held the pager, shown now.
                                turnToPlayer(animate = true)
                            }
                        }
                    }

                    // Only a swipe asks for a track: a page selected by the pager being turned after the
                    // player is the player's own track already. Auxio's `UserAwarePagerCallback`.
                    override fun onPageSelected(position: Int) {
                        if (!isUserSwiping) return
                        val track = coverAdapter.currentList.getOrNull(position) ?: return
                        swipedIndex = position
                        // Posting the queue goto command prevents the seekbar pos from desyncing
                        // from the song's duration, which creates a visual flicker in the seekbar.
                        post { onSwipeToTrack(track) }
                    }
                },
            )
            setPageTransformer(carousel)
            recycler().apply {
                // Make it possible to collapse the bottom sheet from the ViewPager's touch area.
                isNestedScrollingEnabled = false
                // A drag past the queue's ends reaches the band, whose edge effect draws no glow.
                overScrollMode = View.OVER_SCROLL_ALWAYS
                edgeEffectFactory = CoverOverscrollEdgeEffectFactory(overscroll)
                addOnItemTouchListener(overscroll)
            }
            // Make it easier to collapse the bottom sheet
            dampen()
            offscreenPageLimit = 1
        }
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Whatever the player reported while the band held the cover, shown now.
        overscroll.onSettle = { turnToPlayer(animate = true) }
    }

    /** Round mode, for the covers' masks - see [CoverPagerAdapter.isRounded]. */
    var isRounded: Boolean
        get() = coverAdapter.isRounded
        set(value) {
            coverAdapter.isRounded = value
        }

    /** Whether a swipe changes track - off while the player is collapsed to its mini bar. */
    var isSwipeEnabled: Boolean
        get() = pager.isUserInputEnabled
        set(value) {
            pager.isUserInputEnabled = value
        }

    /**
     * Shows [queue], turned to the player's slot [index] - once the frame the change itself redraws
     * has been drawn. A track change redraws the whole player around the cover, and a slide started
     * inside that frame spends its duration there and lands as a jump. Auxio's `updatePager`.
     */
    fun show(queue: List<QueueTrack>, index: Int) {
        if (queue == playerQueue && index == playerIndex) return
        playerQueue = queue
        playerIndex = index

        pager.apply {
            if (!isAttachedToWindow) {
                post { applyPlayerState() }
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isHardwareAccelerated) {
                // New version using post-Q frame hooks
                viewTreeObserver.registerFrameCommitCallback {
                    post { postOnAnimation { applyPlayerState() } }
                }
                postInvalidateOnAnimation()
            } else {
                // Let current layout happen, then wait for the next to conclude
                postOnAnimation { postOnAnimation { applyPlayerState() } }
            }
        }
    }

    /** Auxio's `updatePagerImpl`, applied to the latest the player reported. */
    private fun applyPlayerState() {
        // Smooth scrolling only really looks best when only stepping next/prev, so an outright
        // change of queue lands on its slot without animating - as Auxio does.
        val isNewQueue = playerQueue.map { it.entry.key } != coverAdapter.currentList.map { it.entry.key }
        if (isNewQueue) swipedIndex = null
        coverAdapter.submitList(playerQueue) { turnToPlayer(animate = !isNewQueue) }
    }

    private fun turnToPlayer(animate: Boolean) {
        if (playerIndex !in 0 until coverAdapter.itemCount) return
        if (isUserSwiping || overscroll.isHolding) return
        swipedIndex?.let { swiped ->
            // Still on its way to the track a swipe asked for: anything before it is out of date.
            if (playerIndex != swiped) return
            swipedIndex = null
        }

        val delta = pager.currentItem - playerIndex
        if (delta == 0) {
            // user scroll, carry on
            return
        }
        // A slide is an animation like any other: none while animations are disabled, a jump instead.
        if (animate && abs(delta) == 1 && SonaEffects.shouldAnimate) {
            pager.smoothScrollByPageTo(playerIndex, CoverSlideDurationMillis)
        } else {
            pager.setCurrentItem(playerIndex, false)
        }
    }
}
