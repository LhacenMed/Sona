package com.lhacenmed.sona.feature.player.swiper

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EdgeEffect
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.lhacenmed.sona.feature.player.RubberBandSettleDurationMillis
import com.lhacenmed.sona.feature.player.RubberBandSettleTension
import com.lhacenmed.sona.feature.player.rubberBandOffset
import com.lhacenmed.sona.feature.player.rubberBandPull

/**
 * How far into a swipe the band can take the cover, as a share of its width: past the carousel's gap
 * breakpoint, so the stretch shows as the cover shrinking in its mask, not only as the gap opening.
 */
private const val CoverStretchReach = 0.3f

/**
 * The band at the ends of the queue. A swipe past the first or last cover, where there is no track to
 * turn to, begins as any swipe does - the cover shrinking in its mask and drifting with it - but with
 * nothing coming in beside it, and held by [rubberBandOffset], so it gives less the farther it is
 * pulled. Released, it falls back to rest. Khatmah's `WirdWall`, with no arm: nothing is reached here.
 *
 * Two framework signals drive it, each doing only what it is actually good for:
 *
 * - the pager's **edge effect** says *when* the covers are exhausted. RecyclerView routes a drag there
 *   only after its own scrolling has taken everything it can, so the band never has to guess whether a
 *   swipe was meant to turn a cover.
 * - an **item-touch listener** then carries the gesture. Once engaged it intercepts, which is
 *   RecyclerView's own supported hand-over: it cancels its scroll and stops competing, so exactly one
 *   thing moves the cover. The pull is measured as absolute displacement from where the band engaged,
 *   so finger wobble and reversals resolve smoothly instead of accumulating.
 *
 * Engaged, the band claims the gesture from the pager's parents exactly as a scrolling pager does, so a
 * swipe past the ends is held against a vertical wobble the same as a swipe that turns a cover.
 *
 * Having taken the gesture, the band keeps it to the finger lifting - RecyclerView has no way to take a
 * gesture back - so it hands the pager whatever it cannot use itself. A finger that comes back through
 * rest and carries on the other way turns the covers, through the pager's own fake drag, exactly as far
 * as a swipe would; lifted, the pager settles as it settles any swipe. Turned back past rest, the band
 * stretches again. One gesture moves the cover naturally both ways, however often it changes its mind.
 *
 * The pull shapes the current cover, never the pager: transforming the scrolling view would shift the
 * coordinates its own touch handling reads back, so each frame of pull would distort the next.
 */
internal class CoverOverscroll(
    private val pager: ViewPager2,
    private val carousel: CarouselTransformer,
) : RecyclerView.OnItemTouchListener {

    /** Told when the cover is back at rest, so what the player reported meanwhile can be shown. */
    var onSettle: () -> Unit = {}

    /** Whether the band has the cover - pulled, or on its way back. */
    val isHolding get() = pulling || spring != null

    // The pull is measured from [anchorX] to the finger the gesture is following. Which finger that is
    // can change mid-gesture, so the anchor moves with it - see [rebase].
    private var anchorX = 0f
    private var lastX = 0f
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var pulling = false

    /** Which way the finger pulls past the end: 1 towards the right, -1 towards the left. */
    private var pullDirection = 1f

    /** The pull the band stood at when engaged, so a grab mid-settle carries on from there. */
    private var engagedPull = 0f

    /**
     * How far the finger has taken the pager back from the end, in px of travel against [pullDirection] -
     * the part of the gesture the band hands the pager as a fake drag.
     */
    private var handedToPagerPx = 0f

    /** How far the band holds the cover into a swipe, in px - positive towards the right. */
    private var stretchPx = 0f
    private var spring: ValueAnimator? = null

    /**
     * The covers have run out towards [direction] and the drag continues: from here the gesture is the
     * band's. Called by the edge effect, so the decision is the pager's own, not a guess about the finger.
     */
    fun engage(direction: Float) {
        if (pulling) return
        pulling = true
        // Claimed from everything above the pager, as RecyclerView claims a drag it scrolls - so a sheet
        // around the player cannot take the gesture on a vertical wobble, whichever way the cover moves.
        pager.recycler().parent.requestDisallowInterceptTouchEvent(true)
        spring?.cancel()
        pullDirection = direction
        engagedPull = rubberBandPull((stretchPx * direction).coerceAtLeast(0f), 0f, stretchLimit())
        anchorX = lastX
    }

    // ── The gesture ─────────────────────────────────────────────────────────────

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        track(e)
        // Taking the gesture only once engaged; until then this is a passive observer and the pager
        // turns covers exactly as it always does.
        return pulling
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) = track(e)

    override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) = Unit

    private fun track(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = e.getPointerId(0)
                lastX = e.x
            }

            // A second finger takes over, exactly as the covers themselves hand over to it.
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = e.actionIndex
                pointerId = e.getPointerId(index)
                rebase(e.getX(index))
            }

            // The followed finger left: carry on with one that remains, from where it is.
            MotionEvent.ACTION_POINTER_UP -> {
                val index = e.actionIndex
                if (e.getPointerId(index) == pointerId) {
                    val next = if (index == 0) 1 else 0
                    pointerId = e.getPointerId(next)
                    rebase(e.getX(next))
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val index = e.findPointerIndex(pointerId)
                if (index < 0) return
                lastX = e.getX(index)
                if (pulling) {
                    // Past the end the travel stretches the band; back through rest it turns the covers.
                    var travel = engagedPull + (lastX - anchorX) * pullDirection
                    if (travel < 0f && handedToPagerPx == 0f && !pager.recycler().canScrollHorizontally(pullDirection.toInt())) {
                        // No cover that way either - a queue of one: the band stretches that way instead.
                        pullDirection = -pullDirection
                        engagedPull = 0f
                        anchorX = lastX + travel * pullDirection
                        travel = -travel
                    }
                    // One of the two holds the cover at a time, and the one letting go does so first:
                    // both shape the same cover, so either moving it under the other would fight it.
                    if (travel >= 0f) {
                        handToPager(0f)
                        val stretch = pullDirection * rubberBandOffset(travel, 0f, stretchLimit())
                        if (stretch != stretchPx) apply(stretch)
                    } else {
                        if (stretchPx != 0f) apply(0f)
                        handToPager(travel)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pulling) {
                pulling = false
                handedToPagerPx = 0f
                // Settled as any swipe is: flung on to the next cover, or snapped back to this one.
                if (pager.isFakeDragging) pager.endFakeDrag()
                springHome(stretchPx)
            }
        }
    }

    /**
     * Turns the covers to [handed] px of travel back from the end (0 or less), by the finger's own
     * movement since the last frame - a fake drag begun as the finger comes back through rest, and
     * ended as it returns there: the band has the cover again, and a drag left open would hand a later
     * release the velocity of this one.
     */
    private fun handToPager(handed: Float) {
        if (handed == handedToPagerPx) return
        if (!pager.isFakeDragging && !pager.beginFakeDrag()) return
        pager.fakeDragBy((handed - handedToPagerPx) * pullDirection)
        handedToPagerPx = handed
        if (handed == 0f) pager.endFakeDrag()
    }

    /**
     * Follows a different finger without moving the cover: the anchor shifts by the same distance as the
     * reference did, so the pull either side of the change is identical.
     */
    private fun rebase(x: Float) {
        anchorX += x - lastX
        lastX = x
    }

    // ── Rendering ───────────────────────────────────────────────────────────────

    /**
     * Holds the current cover [px] into a swipe, shaped by the carousel as a real swipe that far would
     * shape it - its neighbours stay hidden, so nothing comes in beside it.
     */
    private fun apply(px: Float) {
        stretchPx = px
        val cover = pager.recycler().layoutManager?.findViewByPosition(pager.currentItem) ?: return
        // A swipe towards the right moves a page towards positive positions, mirrored in RTL.
        val direction = if (pager.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
        carousel.shape(cover, px / pager.width * direction)
    }

    private fun springHome(from: Float) {
        if (from == 0f) {
            onSettle()
            return
        }
        spring = ValueAnimator.ofFloat(from, 0f).apply {
            duration = RubberBandSettleDurationMillis.toLong()
            interpolator = DecelerateInterpolator(RubberBandSettleTension)
            addUpdateListener { apply(it.animatedValue as Float) }
            doOnEnd {
                spring = null
                onSettle()
            }
            start()
        }
    }

    private fun stretchLimit() = pager.width * CoverStretchReach
}

/** Wakes [overscroll] when either end of the queue is reached. */
internal class CoverOverscrollEdgeEffectFactory(
    private val overscroll: CoverOverscroll,
) : RecyclerView.EdgeEffectFactory() {

    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect =
        when (direction) {
            // Pulled at the left edge the finger travels right, and at the right edge left.
            DIRECTION_LEFT -> CoverEdgeEffect(view, overscroll, pullDirection = 1f)
            DIRECTION_RIGHT -> CoverEdgeEffect(view, overscroll, pullDirection = -1f)
            else -> super.createEdgeEffect(view, direction)
        }
}

/**
 * Reports "the covers are exhausted" to [overscroll] and draws nothing. It deliberately handles no other
 * callback: the glow's release and absorb are about drawing a glow, not about the finger.
 */
private class CoverEdgeEffect(
    host: RecyclerView,
    private val overscroll: CoverOverscroll,
    private val pullDirection: Float,
) : EdgeEffect(host.context) {

    override fun onPull(deltaDistance: Float) = overscroll.engage(pullDirection)

    override fun onPull(deltaDistance: Float, displacement: Float) = overscroll.engage(pullDirection)

    /** Nothing to draw: the band is the cover's own shape. */
    override fun draw(canvas: Canvas): Boolean = false

    /** Never animating, so the pager never invalidates or releases on the band's behalf. */
    override fun isFinished(): Boolean = true
}
